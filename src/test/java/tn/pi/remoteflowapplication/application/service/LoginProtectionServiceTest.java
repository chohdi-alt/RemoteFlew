package tn.pi.remoteflowapplication.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.config.LoginProtectionProperties;
import tn.pi.remoteflowapplication.domain.entity.LoginProtectionState;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringLoginProtectionStateJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginProtectionServiceTest {

    @Mock
    private SpringLoginProtectionStateJpaRepository stateRepository;

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    private LoginProtectionService service;

    @BeforeEach
    void setUp() {
        LoginProtectionProperties properties = new LoginProtectionProperties();
        properties.setHardLockThresholdFailures(2);
        properties.setHardLockThresholdLockEvents(1);
        properties.setFailureWindowMinutes(30);
        properties.setTemporaryLockDedupSeconds(300);
        service = new LoginProtectionService(stateRepository, keycloakAuthService, userRepository, properties);
    }

    @Test
    void shouldTrackCredentialFailure() {
        LoginProtectionState state = new LoginProtectionState("alice");
        when(stateRepository.findByUsernameForUpdate("alice")).thenReturn(Optional.of(state));

        AuthenticationFailedException failure = new AuthenticationFailedException(
                "Invalid credentials",
                "AUTHENTICATION_FAILED",
                "invalid_grant",
                "Invalid user credentials",
                401);

        service.onLoginFailure("Alice", failure);

        ArgumentCaptor<LoginProtectionState> captor = ArgumentCaptor.forClass(LoginProtectionState.class);
        verify(stateRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getFailureCount());
        assertEquals(0, captor.getValue().getTemporaryLockCount());
    }

    @Test
    void shouldEscalateToHardLockAfterRepeatedAbuse() {
        LoginProtectionState state = new LoginProtectionState("alice");
        Instant baseline = Instant.now();
        state.registerCredentialFailure(baseline.minusSeconds(10), Duration.ofMinutes(30));
        state.registerCredentialFailure(baseline.minusSeconds(5), Duration.ofMinutes(30));
        when(stateRepository.findByUsernameForUpdate("alice")).thenReturn(Optional.of(state));
        when(keycloakAuthService.findUserIdByUsername("alice")).thenReturn(Optional.of("kc-1"));
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));

        AuthenticationFailedException temporaryLockFailure = new AuthenticationFailedException(
                "Account temporarily disabled",
                "AUTHENTICATION_FAILED",
                "invalid_grant",
                "Account temporarily disabled due to failed logins",
                401);

        service.onLoginFailure("alice", temporaryLockFailure);

        verify(keycloakAuthService).setEnabled("kc-1", false);
        verify(user).updateActivation(false);
        verify(userRepository).save(user);
        verify(stateRepository).save(any(LoginProtectionState.class));
        assertNotNull(state.getHardLockedAt());
        assertTrue(state.isHardLocked());
    }

    @Test
    void shouldSkipTrackingWhenPasswordUpdateRequired() {
        AuthenticationFailedException passwordUpdateRequired = new AuthenticationFailedException(
                "Password update required before login.",
                "PASSWORD_UPDATE_REQUIRED",
                "invalid_grant",
                "Account requires update password action",
                400);

        service.onLoginFailure("alice", passwordUpdateRequired);

        verify(stateRepository, never()).findByUsernameForUpdate(any());
        verify(stateRepository, never()).save(any());
    }

    @Test
    void shouldClearTrackingOnLoginSuccess() {
        service.onLoginSuccess("Alice");
        verify(stateRepository).deleteByUsername(eq("alice"));
    }
}
