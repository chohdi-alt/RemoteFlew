package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "activation_tokens")
public class ActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "keycloak_user_id", nullable = false, length = 64)
    private String keycloakUserId;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ActivationTokenStatus status = ActivationTokenStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivationToken() {
    }

    public ActivationToken(
            User user,
            String keycloakUserId,
            String username,
            String email,
            String tokenHash,
            Instant expiresAt) {
        this.user = user;
        this.keycloakUserId = keycloakUserId;
        this.username = username;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.status = ActivationTokenStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public ActivationTokenStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markConsumed(Instant when) {
        this.status = ActivationTokenStatus.CONSUMED;
        this.consumedAt = when == null ? Instant.now() : when;
    }

    public void markRevoked(Instant when) {
        this.status = ActivationTokenStatus.REVOKED;
        this.revokedAt = when == null ? Instant.now() : when;
    }

    public boolean isExpired(Instant now) {
        Instant reference = now == null ? Instant.now() : now;
        return expiresAt != null && expiresAt.isBefore(reference);
    }
}
