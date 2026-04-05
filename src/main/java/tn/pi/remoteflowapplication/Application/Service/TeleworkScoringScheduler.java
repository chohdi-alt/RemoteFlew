package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TeleworkScoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(TeleworkScoringScheduler.class);

    private final TeleworkScoringService teleworkScoringService;

    public TeleworkScoringScheduler(TeleworkScoringService teleworkScoringService) {
        this.teleworkScoringService = teleworkScoringService;
    }

    @Scheduled(cron = "${telework.scoring.scheduler-cron:0 0 * * * *}")
    public void createPendingScores() {
        int created = teleworkScoringService.createPendingScoresForEndedApprovedRequests();
        if (created > 0) {
            log.info("Scheduled scoring scan created {} pending score row(s).", created);
        }
    }

    public int runNow() {
        return teleworkScoringService.createPendingScoresForEndedApprovedRequests();
    }
}
