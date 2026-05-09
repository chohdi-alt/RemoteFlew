package tn.pi.remoteflowapplication.infrastructure.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.pi.remoteflowapplication.application.service.KeycloakUserSyncService;

@Component
@Profile("!e2e")
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakUserBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserBootstrap.class);

    private final KeycloakUserSyncService syncService;

    public KeycloakUserBootstrap(KeycloakUserSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            logger.info("Starting Keycloak user and role synchronization to database...");
            syncService.synchronizeUsersAndRoles();
            logger.info("Keycloak synchronization completed successfully.");
        } catch (Exception e) {
            logger.warn("Keycloak synchronization failed; application startup continues. message={}", e.getMessage(), e);
        }
    }
}
