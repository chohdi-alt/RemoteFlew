package tn.pi.remoteflowapplication.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!e2e")
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakBruteForceProtectionInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakBruteForceProtectionInitializer.class);

    private final Keycloak keycloak;
    private final String realm;
    private final LoginProtectionProperties properties;

    public KeycloakBruteForceProtectionInitializer(
            Keycloak keycloak,
            @Value("${keycloak.realm}") String realm,
            LoginProtectionProperties properties) {
        this.keycloak = keycloak;
        this.realm = realm;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isBootstrapKeycloakBruteForce()) {
            logger.info("event=AUTH_BRUTE_FORCE_BOOTSTRAP_SKIPPED reason=bootstrap-disabled");
            return;
        }

        LoginProtectionProperties.KeycloakBruteForce target = properties.getKeycloak();
        if (!target.isEnabled()) {
            logger.info("event=AUTH_BRUTE_FORCE_BOOTSTRAP_SKIPPED reason=keycloak-brute-force-disabled");
            return;
        }

        try {
            RealmResource realmResource = keycloak.realm(realm);
            RealmRepresentation current = realmResource.toRepresentation();
            boolean changed = applyTargetConfiguration(current, target);

            if (changed) {
                realmResource.update(current);
                logger.info(
                        "event=AUTH_BRUTE_FORCE_BOOTSTRAPPED realm={} maxFailures={} waitIncrementSeconds={} quickLoginCheckMillis={} minimumQuickLoginWaitSeconds={} maxWaitSeconds={} failureResetSeconds={} permanentLockout={}",
                        realm,
                        target.getMaxLoginFailures(),
                        target.getWaitIncrementSeconds(),
                        target.getQuickLoginCheckMillis(),
                        target.getMinimumQuickLoginWaitSeconds(),
                        target.getMaxWaitSeconds(),
                        target.getFailureResetSeconds(),
                        target.isPermanentLockout());
            } else {
                logger.info("event=AUTH_BRUTE_FORCE_ALREADY_COMPLIANT realm={}", realm);
            }
        } catch (Exception ex) {
            logger.error(
                    "event=AUTH_BRUTE_FORCE_BOOTSTRAP_FAILED realm={} message={}",
                    realm,
                    ex.getMessage(),
                    ex);
        }
    }

    boolean applyTargetConfiguration(
            RealmRepresentation realmRepresentation,
            LoginProtectionProperties.KeycloakBruteForce target) {
        boolean changed = false;

        changed |= setBooleanIfChanged(realmRepresentation.isBruteForceProtected(), true,
                realmRepresentation::setBruteForceProtected);
        changed |= setIntegerIfChanged(realmRepresentation.getFailureFactor(), target.getMaxLoginFailures(),
                realmRepresentation::setFailureFactor);
        changed |= setIntegerIfChanged(
                realmRepresentation.getWaitIncrementSeconds(),
                target.getWaitIncrementSeconds(),
                realmRepresentation::setWaitIncrementSeconds);
        changed |= setLongIfChanged(
                realmRepresentation.getQuickLoginCheckMilliSeconds(),
                target.getQuickLoginCheckMillis(),
                realmRepresentation::setQuickLoginCheckMilliSeconds);
        changed |= setIntegerIfChanged(
                realmRepresentation.getMinimumQuickLoginWaitSeconds(),
                target.getMinimumQuickLoginWaitSeconds(),
                realmRepresentation::setMinimumQuickLoginWaitSeconds);
        changed |= setIntegerIfChanged(
                realmRepresentation.getMaxFailureWaitSeconds(),
                target.getMaxWaitSeconds(),
                realmRepresentation::setMaxFailureWaitSeconds);
        changed |= setIntegerIfChanged(
                realmRepresentation.getMaxDeltaTimeSeconds(),
                target.getFailureResetSeconds(),
                realmRepresentation::setMaxDeltaTimeSeconds);
        changed |= setBooleanIfChanged(
                realmRepresentation.isPermanentLockout(),
                target.isPermanentLockout(),
                realmRepresentation::setPermanentLockout);

        return changed;
    }

    private boolean setBooleanIfChanged(Boolean current, boolean expected,
            java.util.function.Consumer<Boolean> setter) {
        if (current != null && current == expected) {
            return false;
        }
        setter.accept(expected);
        return true;
    }

    private boolean setIntegerIfChanged(Integer current, int expected, java.util.function.Consumer<Integer> setter) {
        if (current != null && current == expected) {
            return false;
        }
        setter.accept(expected);
        return true;
    }

    private boolean setLongIfChanged(Long current, long expected, java.util.function.Consumer<Long> setter) {
        if (current != null && current == expected) {
            return false;
        }
        setter.accept(expected);
        return true;
    }
}
