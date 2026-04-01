package tn.pi.remoteflowapplication.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.domain.entity.ActivationToken;
import tn.pi.remoteflowapplication.domain.entity.ActivationTokenStatus;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringActivationTokenJpaRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ActivationTokenService {

    private final SpringActivationTokenJpaRepository activationTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration defaultTtl;

    public ActivationTokenService(
            SpringActivationTokenJpaRepository activationTokenRepository,
            @Value("${security.activation-token.ttl-minutes:1440}") long activationTokenTtlMinutes) {
        this.activationTokenRepository = activationTokenRepository;
        this.defaultTtl = Duration.ofMinutes(Math.max(1, activationTokenTtlMinutes));
    }

    @Transactional
    public IssuedActivationToken issueToken(
            User user,
            String keycloakUserId,
            String username,
            String email) {
        String normalizedKeycloakUserId = normalizeRequired(keycloakUserId);
        String normalizedUsername = normalizeRequired(username);

        revokeExistingPendingTokens(normalizedKeycloakUserId);

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(defaultTtl);

        ActivationToken token = new ActivationToken(
                user,
                normalizedKeycloakUserId,
                normalizedUsername,
                normalizeOptional(email),
                tokenHash,
                expiresAt);
        activationTokenRepository.save(token);

        return new IssuedActivationToken(rawToken, tokenHash, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<ActivationToken> findByRawToken(String rawToken) {
        String normalizedRawToken = normalizeOptional(rawToken);
        if (normalizedRawToken == null) {
            return Optional.empty();
        }
        return activationTokenRepository.findByTokenHash(hashToken(normalizedRawToken));
    }

    @Transactional(readOnly = true)
    public Optional<ActivationToken> findUsableToken(String rawToken) {
        return findByRawToken(rawToken)
                .filter(token -> token.getStatus() == ActivationTokenStatus.PENDING)
                .filter(token -> !token.isExpired(Instant.now()));
    }

    @Transactional
    public Optional<ActivationToken> consumeToken(String rawToken) {
        Optional<ActivationToken> optional = findByRawToken(rawToken);
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        ActivationToken token = optional.get();
        if (token.getStatus() != ActivationTokenStatus.PENDING || token.isExpired(Instant.now())) {
            return Optional.empty();
        }

        token.markConsumed(Instant.now());
        return Optional.of(activationTokenRepository.save(token));
    }

    @Transactional
    public void revokeExistingPendingTokens(String keycloakUserId) {
        String normalizedKeycloakUserId = normalizeRequired(keycloakUserId);
        activationTokenRepository.findByKeycloakUserIdAndStatus(normalizedKeycloakUserId, ActivationTokenStatus.PENDING)
                .forEach(token -> token.markRevoked(Instant.now()));
    }

    public String hashToken(String rawToken) {
        String normalizedRawToken = normalizeRequired(rawToken);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalizedRawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash activation token", ex);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Activation token value must not be blank.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public record IssuedActivationToken(
            String rawToken,
            String tokenHash,
            Instant expiresAt
    ) {
    }
}
