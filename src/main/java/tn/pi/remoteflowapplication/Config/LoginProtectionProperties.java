package tn.pi.remoteflowapplication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "security.login-protection")
public class LoginProtectionProperties {

    private boolean bootstrapKeycloakBruteForce = true;
    private boolean hardLockEnabled = true;
    private int hardLockThresholdFailures = 10;
    private int hardLockThresholdLockEvents = 2;
    private int failureWindowMinutes = 30;
    private int temporaryLockDedupSeconds = 300;
    private final KeycloakBruteForce keycloak = new KeycloakBruteForce();

    public boolean isBootstrapKeycloakBruteForce() {
        return bootstrapKeycloakBruteForce;
    }

    public void setBootstrapKeycloakBruteForce(boolean bootstrapKeycloakBruteForce) {
        this.bootstrapKeycloakBruteForce = bootstrapKeycloakBruteForce;
    }

    public boolean isHardLockEnabled() {
        return hardLockEnabled;
    }

    public void setHardLockEnabled(boolean hardLockEnabled) {
        this.hardLockEnabled = hardLockEnabled;
    }

    public int getHardLockThresholdFailures() {
        return hardLockThresholdFailures;
    }

    public void setHardLockThresholdFailures(int hardLockThresholdFailures) {
        this.hardLockThresholdFailures = hardLockThresholdFailures;
    }

    public int getHardLockThresholdLockEvents() {
        return hardLockThresholdLockEvents;
    }

    public void setHardLockThresholdLockEvents(int hardLockThresholdLockEvents) {
        this.hardLockThresholdLockEvents = hardLockThresholdLockEvents;
    }

    public int getFailureWindowMinutes() {
        return failureWindowMinutes;
    }

    public void setFailureWindowMinutes(int failureWindowMinutes) {
        this.failureWindowMinutes = failureWindowMinutes;
    }

    public int getTemporaryLockDedupSeconds() {
        return temporaryLockDedupSeconds;
    }

    public void setTemporaryLockDedupSeconds(int temporaryLockDedupSeconds) {
        this.temporaryLockDedupSeconds = temporaryLockDedupSeconds;
    }

    public KeycloakBruteForce getKeycloak() {
        return keycloak;
    }

    public Duration failureWindow() {
        return Duration.ofMinutes(Math.max(1, failureWindowMinutes));
    }

    public Duration temporaryLockDedupWindow() {
        return Duration.ofSeconds(Math.max(1, temporaryLockDedupSeconds));
    }

    public static class KeycloakBruteForce {

        private boolean enabled = true;
        private int maxLoginFailures = 5;
        private int waitIncrementSeconds = 60;
        private long quickLoginCheckMillis = 1000L;
        private int minimumQuickLoginWaitSeconds = 60;
        private int maxWaitSeconds = 300;
        private int failureResetSeconds = 900;
        private boolean permanentLockout = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxLoginFailures() {
            return maxLoginFailures;
        }

        public void setMaxLoginFailures(int maxLoginFailures) {
            this.maxLoginFailures = maxLoginFailures;
        }

        public int getWaitIncrementSeconds() {
            return waitIncrementSeconds;
        }

        public void setWaitIncrementSeconds(int waitIncrementSeconds) {
            this.waitIncrementSeconds = waitIncrementSeconds;
        }

        public long getQuickLoginCheckMillis() {
            return quickLoginCheckMillis;
        }

        public void setQuickLoginCheckMillis(long quickLoginCheckMillis) {
            this.quickLoginCheckMillis = quickLoginCheckMillis;
        }

        public int getMinimumQuickLoginWaitSeconds() {
            return minimumQuickLoginWaitSeconds;
        }

        public void setMinimumQuickLoginWaitSeconds(int minimumQuickLoginWaitSeconds) {
            this.minimumQuickLoginWaitSeconds = minimumQuickLoginWaitSeconds;
        }

        public int getMaxWaitSeconds() {
            return maxWaitSeconds;
        }

        public void setMaxWaitSeconds(int maxWaitSeconds) {
            this.maxWaitSeconds = maxWaitSeconds;
        }

        public int getFailureResetSeconds() {
            return failureResetSeconds;
        }

        public void setFailureResetSeconds(int failureResetSeconds) {
            this.failureResetSeconds = failureResetSeconds;
        }

        public boolean isPermanentLockout() {
            return permanentLockout;
        }

        public void setPermanentLockout(boolean permanentLockout) {
            this.permanentLockout = permanentLockout;
        }
    }
}
