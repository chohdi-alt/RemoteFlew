package tn.pi.remoteflowapplication.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import tn.pi.remoteflowapplication.application.dto.ManagerScoreSubmissionRequest;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.entity.TeleworkScore;
import tn.pi.remoteflowapplication.domain.exception.BadRequestException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkScoreJpaRepository;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeleworkScoringServiceTest {

    @Mock
    private SpringTeleworkJpaRepository teleworkRequestJpaRepository;

    @Mock
    private SpringTeleworkScoreJpaRepository teleworkScoreRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScoringEngine scoringEngine;

    @Mock
    private EmailNotificationService emailNotificationService;

    private TeleworkScoringService teleworkScoringService;

    @BeforeEach
    void setUp() {
        TaskExecutor sameThreadExecutor = Runnable::run;
        teleworkScoringService = new TeleworkScoringService(
                teleworkRequestJpaRepository,
                teleworkScoreRepository,
                teamRepository,
                userRepository,
                scoringEngine,
                emailNotificationService,
                sameThreadExecutor);
    }

    @Test
    void shouldCreatePendingScoresForEligibleRequests() {
        TeleworkRequest request = approvedEndedRequest();
        when(teleworkRequestJpaRepository.findApprovedEndedBeforeWithoutScore(any(LocalDate.class)))
                .thenReturn(List.of(request));
        when(teleworkScoreRepository.save(any(TeleworkScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int created = teleworkScoringService.createPendingScoresForEndedApprovedRequests(LocalDate.now());

        assertEquals(1, created);
        verify(teleworkScoreRepository).save(any(TeleworkScore.class));
    }

    @Test
    void shouldRequireCommentWhenComputedTotalIsLow() {
        TeleworkRequest request = approvedEndedRequest();
        TeleworkScore score = TeleworkScore.createPending(request);

        when(teleworkScoreRepository.findByRequestIdWithMetrics(55L)).thenReturn(java.util.Optional.of(score));
        when(teamRepository.findManagedTeamIdsByUsername("manager.user")).thenReturn(List.of(request.getTeamId()));
        when(scoringEngine.calculate(any(), any(), any(), any())).thenReturn(new ScoringEngine.ScoreResult(
                new BigDecimal("55.00"),
                List.of()));

        ManagerScoreSubmissionRequest payload = new ManagerScoreSubmissionRequest(50, 60, 55, 55, " ");

        assertThrows(BadRequestException.class, () -> teleworkScoringService.submitManagerScore(
                55L,
                payload,
                "manager.user",
                "manager-sub-id"));

        verify(teleworkScoreRepository, never()).save(any(TeleworkScore.class));
    }

    private TeleworkRequest approvedEndedRequest() {
        TeleworkRequest request = TeleworkRequest.create(
                "employee.user",
                LocalDate.now().minusDays(7),
                LocalDate.now().minusDays(2));
        request.approveByManager("approved by manager");
        request.approveByHR("approved by hr");
        request.assignTeamId(12L);
        assignId(request, 1001L);
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new IllegalStateException("Test setup failed: request is not approved.");
        }
        return request;
    }

    private void assignId(TeleworkRequest request, Long idValue) {
        try {
            Field idField = TeleworkRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(request, idValue);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set test request id", ex);
        }
    }
}
