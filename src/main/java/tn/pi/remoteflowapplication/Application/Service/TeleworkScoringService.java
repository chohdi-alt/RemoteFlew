package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tn.pi.remoteflowapplication.application.dto.HrScoreReviewRequest;
import tn.pi.remoteflowapplication.application.dto.ManagerScoreSubmissionRequest;
import tn.pi.remoteflowapplication.application.dto.TeleworkScoreDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkScoreMetricDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkScoreSummaryDTO;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.entity.TeleworkScore;
import tn.pi.remoteflowapplication.domain.entity.TeleworkScoreMetric;
import tn.pi.remoteflowapplication.domain.exception.BadRequestException;
import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;
import tn.pi.remoteflowapplication.domain.exception.ResourceNotFoundException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.domain.state.ScoreStatus;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkScoreJpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class TeleworkScoringService {

    private static final Logger log = LoggerFactory.getLogger(TeleworkScoringService.class);
    private static final BigDecimal LOW_SCORE_THRESHOLD = BigDecimal.valueOf(60);

    private final SpringTeleworkJpaRepository teleworkRequestJpaRepository;
    private final SpringTeleworkScoreJpaRepository teleworkScoreRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final ScoringEngine scoringEngine;
    private final EmailNotificationService emailNotificationService;
    private final TaskExecutor notificationTaskExecutor;

    public TeleworkScoringService(
            SpringTeleworkJpaRepository teleworkRequestJpaRepository,
            SpringTeleworkScoreJpaRepository teleworkScoreRepository,
            TeamRepository teamRepository,
            UserRepository userRepository,
            ScoringEngine scoringEngine,
            EmailNotificationService emailNotificationService,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor) {
        this.teleworkRequestJpaRepository = teleworkRequestJpaRepository;
        this.teleworkScoreRepository = teleworkScoreRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.scoringEngine = scoringEngine;
        this.emailNotificationService = emailNotificationService;
        this.notificationTaskExecutor = notificationTaskExecutor;
    }

    @Transactional
    public int createPendingScoresForEndedApprovedRequests() {
        return createPendingScoresForEndedApprovedRequests(LocalDate.now());
    }

    @Transactional
    public int createPendingScoresForEndedApprovedRequests(LocalDate referenceDate) {
        LocalDate cursorDate = referenceDate == null ? LocalDate.now() : referenceDate;
        List<TeleworkRequest> eligibleRequests = teleworkRequestJpaRepository
                .findApprovedEndedBeforeWithoutScore(cursorDate);

        int created = 0;
        for (TeleworkRequest request : eligibleRequests) {
            if (request == null || request.getId() == null) {
                continue;
            }
            try {
                TeleworkScore score = TeleworkScore.createPending(request);
                TeleworkScore saved = teleworkScoreRepository.save(score);
                created++;
                log.info("[SCORING_CREATED] requestId={} scoreId={}", request.getId(), saved.getId());
                notifyManagerReminderAfterCommit(request);
            } catch (DataIntegrityViolationException ex) {
                log.debug("Skipping duplicate pending score for requestId={}", request.getId());
            }
        }

        return created;
    }

    @Transactional(readOnly = true)
    public List<TeleworkScoreSummaryDTO> getPendingScoresForManager(String managerUsername) {
        List<Long> managedTeamIds = teamRepository.findManagedTeamIdsByUsername(managerUsername);
        if (managedTeamIds == null || managedTeamIds.isEmpty()) {
            return List.of();
        }

        return teleworkScoreRepository.findByStatusAndTeamIds(ScoreStatus.PENDING_MANAGER_INPUT, managedTeamIds)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public TeleworkScoreDTO submitManagerScore(
            Long requestId,
            ManagerScoreSubmissionRequest command,
            String managerUsername,
            String managerExternalIdFromJwt) {
        TeleworkScore score = teleworkScoreRepository.findByRequestIdWithMetrics(requestId)
                .orElseThrow(() -> new BadRequestException("No scoring record exists for this request."));

        TeleworkRequest request = score.getTeleworkRequest();
        ensureRequestEligibilityForScoring(request);
        ensureManagerOwnsRequestTeam(request, managerUsername);
        ensureManagerSubmissionState(score);

        ScoringEngine.ScoreResult result = scoringEngine.calculate(
                decimalOf(command.attendance()),
                decimalOf(command.tasks()),
                decimalOf(command.punctuality()),
                decimalOf(command.behavior()));

        String normalizedComment = normalize(command.comments());
        if (result.totalScore().compareTo(LOW_SCORE_THRESHOLD) < 0 && normalizedComment == null) {
            throw new BadRequestException("Justification comment is required for low scores.");
        }

        List<TeleworkScoreMetric> metrics = result.metrics().stream()
                .map(this::toMetricEntity)
                .toList();

        String managerExternalId = normalize(managerExternalIdFromJwt);
        if (managerExternalId == null) {
            managerExternalId = normalize(managerUsername);
        }

        score.submitByManager(
                managerExternalId,
                normalizedComment,
                result.totalScore(),
                metrics,
                Instant.now());

        TeleworkScore saved = teleworkScoreRepository.save(score);
        notifyHrAfterCommit(saved);
        return toDetails(saved);
    }

    @Transactional(readOnly = true)
    public List<TeleworkScoreSummaryDTO> searchScoresForHr(
            Long teamId,
            String employeeId,
            String managerExternalId,
            ScoreStatus status) {
        return teleworkScoreRepository.searchForHr(
                        teamId,
                        normalize(employeeId),
                        normalize(managerExternalId),
                        status)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeleworkScoreDTO getScoreDetailsForHr(Long scoreId) {
        TeleworkScore score = teleworkScoreRepository.findByIdWithMetrics(scoreId)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found."));
        return toDetails(score);
    }

    @Transactional(readOnly = true)
    public Optional<TeleworkScoreDTO> findScoreByRequestId(Long requestId) {
        return teleworkScoreRepository.findByRequestIdWithMetrics(requestId).map(this::toDetails);
    }

    @Transactional
    public TeleworkScoreDTO reviewByHr(Long scoreId, HrScoreReviewRequest reviewRequest, String hrExternalIdFromJwt) {
        TeleworkScore score = teleworkScoreRepository.findByIdWithMetrics(scoreId)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found."));

        if (score.getStatus() == ScoreStatus.REVIEWED_BY_HR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Score has already been reviewed.");
        }
        if (score.getStatus() != ScoreStatus.SCORED) {
            throw new BadRequestException("Only scored entries can be reviewed by HR.");
        }

        String hrExternalId = normalize(hrExternalIdFromJwt);
        if (hrExternalId == null) {
            throw new BadRequestException("HR external id is missing from authentication context.");
        }

        score.reviewByHr(hrExternalId, normalize(reviewRequest.comments()), Instant.now());
        TeleworkScore saved = teleworkScoreRepository.save(score);
        return toDetails(saved);
    }

    private void ensureRequestEligibilityForScoring(TeleworkRequest request) {
        if (request == null) {
            throw new BadRequestException("Telework request not found.");
        }
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new BadRequestException("Only approved telework requests can be scored.");
        }
        if (request.getEndDate() == null || !request.getEndDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Scoring is only available after the telework end date.");
        }
    }

    private void ensureManagerOwnsRequestTeam(TeleworkRequest request, String managerUsername) {
        Long requestTeamId = request.getTeamId();
        if (requestTeamId == null) {
            throw new ForbiddenOperationException("Request is not linked to a team.");
        }

        List<Long> managedTeamIds = teamRepository.findManagedTeamIdsByUsername(managerUsername);
        boolean allowed = managedTeamIds != null && managedTeamIds.contains(requestTeamId);
        if (!allowed) {
            throw new ForbiddenOperationException("You can only score requests from teams you manage.");
        }
    }

    private void ensureManagerSubmissionState(TeleworkScore score) {
        if (score.getStatus() == ScoreStatus.SCORED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is already scored.");
        }
        if (score.getStatus() == ScoreStatus.REVIEWED_BY_HR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Score is already reviewed and immutable.");
        }
        if (score.getStatus() != ScoreStatus.PENDING_MANAGER_INPUT) {
            throw new BadRequestException("Score is not in a manager-submittable state.");
        }
    }

    private TeleworkScoreMetric toMetricEntity(ScoringEngine.MetricBreakdown breakdown) {
        return new TeleworkScoreMetric(
                breakdown.metricCode(),
                breakdown.metricValue(),
                breakdown.metricWeight(),
                breakdown.weightedScore());
    }

    private void notifyManagerReminderAfterCommit(TeleworkRequest request) {
        String managerEmail = resolveManagerEmail(request).orElse(null);
        if (managerEmail == null) {
            return;
        }
        Long requestId = request.getId();
        runAfterCommit(() -> notificationTaskExecutor.execute(() ->
                emailNotificationService.notifyManagerScoringReminder(managerEmail, requestId)));
    }

    private Optional<String> resolveManagerEmail(TeleworkRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        if (request.getTeamId() != null) {
            Optional<String> fromTeamManager = teamRepository.findById(request.getTeamId())
                    .map(t -> t.getManager())
                    .map(tn.pi.remoteflowapplication.domain.entity.User::getEmail)
                    .map(this::normalize);
            if (fromTeamManager.isPresent()) {
                return fromTeamManager;
            }
        }

        String managerExternalId = normalize(request.getManagerExternalId());
        if (managerExternalId != null) {
            return userRepository.findByExternalId(managerExternalId)
                    .map(tn.pi.remoteflowapplication.domain.entity.User::getEmail)
                    .map(this::normalize);
        }
        return Optional.empty();
    }

    private void notifyHrAfterCommit(TeleworkScore score) {
        Long requestId = score.getTeleworkRequest().getId();
        String managerExternalId = score.getManagerExternalId();
        BigDecimal totalScore = score.getTotalScore();

        List<String> hrEmails = userRepository.findByRoles_Name("HR").stream()
                .map(tn.pi.remoteflowapplication.domain.entity.User::getEmail)
                .map(this::normalize)
                .filter(email -> email != null)
                .distinct()
                .toList();

        if (hrEmails.isEmpty()) {
            return;
        }

        runAfterCommit(() -> notificationTaskExecutor.execute(() -> hrEmails.forEach(email ->
                emailNotificationService.notifyHrOfNewScore(email, requestId, managerExternalId, totalScore))));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private TeleworkScoreSummaryDTO toSummary(TeleworkScore score) {
        TeleworkRequest request = score.getTeleworkRequest();
        return new TeleworkScoreSummaryDTO(
                score.getId(),
                request.getId(),
                request.getEmployeeId(),
                request.getTeamId(),
                request.getStartDate(),
                request.getEndDate(),
                score.getStatus(),
                score.getTotalScore(),
                score.getManagerExternalId(),
                score.getScoredAt(),
                score.getHrExternalId(),
                score.getReviewedAt());
    }

    private TeleworkScoreDTO toDetails(TeleworkScore score) {
        TeleworkRequest request = score.getTeleworkRequest();
        List<TeleworkScoreMetricDTO> metrics = score.getMetrics().stream()
                .map(metric -> new TeleworkScoreMetricDTO(
                        metric.getMetricCode(),
                        metric.getMetricValue(),
                        metric.getMetricWeight(),
                        metric.getWeightedScore()))
                .toList();

        return new TeleworkScoreDTO(
                score.getId(),
                request.getId(),
                request.getEmployeeId(),
                request.getTeamId(),
                request.getStartDate(),
                request.getEndDate(),
                score.getStatus(),
                score.getTotalScore(),
                score.getManagerExternalId(),
                score.getManagerComment(),
                score.getScoredAt(),
                score.getHrExternalId(),
                score.getHrComment(),
                score.getReviewedAt(),
                score.getCreatedAt(),
                score.getUpdatedAt(),
                metrics);
    }

    private BigDecimal decimalOf(Integer value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value.longValue());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
