package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.application.dto.HrScoreReviewRequest;
import tn.pi.remoteflowapplication.application.dto.ManagerScoreSubmissionRequest;
import tn.pi.remoteflowapplication.application.dto.TeleworkScoreDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkScoreSummaryDTO;
import tn.pi.remoteflowapplication.application.service.TeleworkScoringScheduler;
import tn.pi.remoteflowapplication.application.service.TeleworkScoringService;
import tn.pi.remoteflowapplication.domain.state.ScoreStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scoring")
public class TeleworkScoringController {

    private final TeleworkScoringService teleworkScoringService;
    private final TeleworkScoringScheduler teleworkScoringScheduler;

    public TeleworkScoringController(
            TeleworkScoringService teleworkScoringService,
            TeleworkScoringScheduler teleworkScoringScheduler) {
        this.teleworkScoringService = teleworkScoringService;
        this.teleworkScoringScheduler = teleworkScoringScheduler;
    }

    @GetMapping("/manager/pending")
    @PreAuthorize("hasRole('MANAGER')")
    public List<TeleworkScoreSummaryDTO> getManagerPendingScores(Authentication authentication) {
        return teleworkScoringService.getPendingScoresForManager(authentication.getName());
    }

    @PostMapping("/{requestId}/submit")
    @PreAuthorize("hasRole('MANAGER')")
    public TeleworkScoreDTO submitManagerScore(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody ManagerScoreSubmissionRequest request,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        return teleworkScoringService.submitManagerScore(
                requestId,
                request,
                authentication.getName(),
                resolveExternalId(jwt, authentication));
    }

    @GetMapping
    @PreAuthorize("hasRole('HR')")
    public List<TeleworkScoreSummaryDTO> searchScoresForHr(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String managerExternalId,
            @RequestParam(required = false) ScoreStatus status) {
        return teleworkScoringService.searchScoresForHr(teamId, employeeId, managerExternalId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public TeleworkScoreDTO getScoreForHr(@PathVariable("id") Long scoreId) {
        return teleworkScoringService.getScoreDetailsForHr(scoreId);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('HR')")
    public TeleworkScoreDTO reviewByHr(
            @PathVariable("id") Long scoreId,
            @Valid @RequestBody HrScoreReviewRequest reviewRequest,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        return teleworkScoringService.reviewByHr(
                scoreId,
                reviewRequest,
                resolveExternalId(jwt, authentication));
    }

    @GetMapping("/request/{requestId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public TeleworkScoreDTO getScoreByRequest(@PathVariable("requestId") Long requestId) {
        return teleworkScoringService.findScoreByRequestId(requestId)
                .orElseThrow(() -> new tn.pi.remoteflowapplication.domain.exception.ResourceNotFoundException("Score not found."));
    }

    @PostMapping("/scheduler/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> runSchedulerNow() {
        int created = teleworkScoringScheduler.runNow();
        return Map.of("created", created);
    }

    private String resolveExternalId(Jwt jwt, Authentication authentication) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject().trim();
        }
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName().trim();
        }
        return null;
    }
}
