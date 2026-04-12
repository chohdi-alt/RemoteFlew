package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "login_protection_state")
public class LoginProtectionState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String username;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "temporary_lock_count", nullable = false)
    private int temporaryLockCount;

    @Column(name = "first_failure_at")
    private Instant firstFailureAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_temporary_lock_at")
    private Instant lastTemporaryLockAt;

    @Column(name = "hard_locked_at")
    private Instant hardLockedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LoginProtectionState() {
    }

    public LoginProtectionState(String username) {
        this.username = normalizeUsername(username);
        this.failureCount = 0;
        this.temporaryLockCount = 0;
    }

    public String getUsername() {
        return username;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int getTemporaryLockCount() {
        return temporaryLockCount;
    }

    public Instant getHardLockedAt() {
        return hardLockedAt;
    }

    public boolean isHardLocked() {
        return hardLockedAt != null;
    }

    public void registerCredentialFailure(Instant now, Duration trackingWindow) {
        applyTrackingWindow(now, trackingWindow);
        if (firstFailureAt == null) {
            firstFailureAt = now;
        }
        lastFailureAt = now;
        failureCount++;
    }

    public boolean registerTemporaryLock(Instant now, Duration trackingWindow, Duration dedupWindow) {
        applyTrackingWindow(now, trackingWindow);

        if (firstFailureAt == null) {
            firstFailureAt = now;
        }
        lastFailureAt = now;

        boolean isNewLockEvent = lastTemporaryLockAt == null
                || lastTemporaryLockAt.isBefore(now.minus(dedupWindow));
        if (isNewLockEvent) {
            temporaryLockCount++;
        }
        lastTemporaryLockAt = now;
        return isNewLockEvent;
    }

    public void markHardLocked(Instant now) {
        hardLockedAt = now;
    }

    public void clearAll() {
        failureCount = 0;
        temporaryLockCount = 0;
        firstFailureAt = null;
        lastFailureAt = null;
        lastTemporaryLockAt = null;
        hardLockedAt = null;
    }

    public boolean shouldEscalateToHardLock(int failureThreshold, int lockEventThreshold) {
        return failureCount >= Math.max(1, failureThreshold)
                && temporaryLockCount >= Math.max(1, lockEventThreshold);
    }

    private void applyTrackingWindow(Instant now, Duration trackingWindow) {
        if (lastFailureAt == null) {
            return;
        }
        Instant cutoff = now.minus(trackingWindow);
        if (lastFailureAt.isBefore(cutoff)) {
            failureCount = 0;
            temporaryLockCount = 0;
            firstFailureAt = null;
            lastFailureAt = null;
            lastTemporaryLockAt = null;
            hardLockedAt = null;
        }
    }

    private String normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            throw new IllegalArgumentException("Username must not be null");
        }
        String normalized = rawUsername.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        return normalized;
    }
}
