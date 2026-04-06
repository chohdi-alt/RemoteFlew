package tn.pi.remoteflowapplication.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import tn.pi.remoteflowapplication.application.dto.CreateUserRequest;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.exception.UserAlreadyExistsException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserServiceTest {

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakUserOnboardingTransactionService keycloakUserOnboardingTransactionService;

    @Mock
    private EmailNotificationService emailNotificationService;

    private KeycloakUserService service;

    @BeforeEach
    void setUp() {
        TaskExecutor sameThreadExecutor = Runnable::run;
        service = new KeycloakUserService(
                keycloakAuthService,
                userRepository,
                keycloakUserOnboardingTransactionService,
                emailNotificationService,
                sameThreadExecutor,
                "http://localhost:4200/activate");
    }

    @Test
    void shouldFailFastWhenEmailAlreadyExistsInDatabase() {
        CreateUserRequest request = new CreateUserRequest(
                "new.user",
                "existing@mailhog.local",
                "New",
                "User",
                Set.of("EMPLOYEE"));

        when(userRepository.findByUsername("new.user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@mailhog.local")).thenReturn(Optional.of(mock(User.class)));

        assertThrows(UserAlreadyExistsException.class, () -> service.createUser(request));

        verify(keycloakAuthService, never()).createUser(anyString(), anyString(), any(), any());
    }

    @Test
    void shouldCompensateInKeycloakWhenLocalPersistenceFails() {
        CreateUserRequest request = new CreateUserRequest(
                "new.user",
                "new.user@mailhog.local",
                "New",
                "User",
                Set.of("EMPLOYEE"));

        when(userRepository.findByUsername("new.user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new.user@mailhog.local")).thenReturn(Optional.empty());
        when(keycloakAuthService.findUserIdByUsername("new.user")).thenReturn(Optional.empty());
        when(keycloakAuthService.findUserIdByEmail("new.user@mailhog.local")).thenReturn(Optional.empty());
        when(keycloakAuthService.createUser("new.user", "new.user@mailhog.local", "New", "User")).thenReturn("kc-001");
        when(keycloakUserOnboardingTransactionService.synchronizeAndIssueActivationToken("kc-001"))
                .thenThrow(new DataIntegrityViolationException("Unique key violation"));

        assertThrows(BusinessException.class, () -> service.createUser(request));

        verify(keycloakAuthService).deleteUser("kc-001");
    }

    @Test
    void shouldContinueUserCreationWhenRequestedRoleIsMissingInKeycloak() {
        CreateUserRequest request = new CreateUserRequest(
                "safe.user",
                "safe.user@mailhog.local",
                "Safe",
                "User",
                Set.of("HR"));

        User syncedUser = new User("kc-002", "safe.user@mailhog.local", "User", "Safe", "safe.user", true);
        ActivationTokenService.IssuedActivationToken token = new ActivationTokenService.IssuedActivationToken(
                "raw-token",
                "hash-token",
                Instant.now().plusSeconds(1800));

        when(userRepository.findByUsername("safe.user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("safe.user@mailhog.local")).thenReturn(Optional.empty());
        when(keycloakAuthService.findUserIdByUsername("safe.user")).thenReturn(Optional.empty());
        when(keycloakAuthService.findUserIdByEmail("safe.user@mailhog.local")).thenReturn(Optional.empty());
        when(keycloakAuthService.createUser("safe.user", "safe.user@mailhog.local", "Safe", "User"))
                .thenReturn("kc-002");
        when(keycloakAuthService.realmRoleExists("HR")).thenReturn(false);
        when(keycloakUserOnboardingTransactionService.synchronizeAndIssueActivationToken("kc-002"))
                .thenReturn(new KeycloakUserOnboardingTransactionService.OnboardingResult(syncedUser, token));

        User createdUser = service.createUser(request);

        assertSame(syncedUser, createdUser);
        verify(keycloakAuthService, never()).setRealmRoles(anyString(), any());
        verify(keycloakAuthService).assignUserToManagedGroup(anyString(), anyString(), any());
    }

    @Test
    void shouldCreateUserSuccessfullyWhenRoleExistsInKeycloak() {
        CreateUserRequest request = new CreateUserRequest(
                "valid.user",
                "valid.user@mailhog.local",
                "Valid",
                "User",
                Set.of("EMPLOYEE"));

        User syncedUser = new User("kc-003", "valid.user@mailhog.local", "User", "Valid", "valid.user", true);
        ActivationTokenService.IssuedActivationToken token = new ActivationTokenService.IssuedActivationToken(
                "raw-token-2",
                "hash-token-2",
                Instant.now().plusSeconds(1800));

        when(userRepository.findByUsername("valid.user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("valid.user@mailhog.local")).thenReturn(Optional.empty());
        when(keycloakAuthService.findUserIdByUsername("valid.user")).thenReturn(Optional.empty());
        when(keycloakAuthService.findUserIdByEmail("valid.user@mailhog.local")).thenReturn(Optional.empty());
        when(keycloakAuthService.createUser("valid.user", "valid.user@mailhog.local", "Valid", "User"))
                .thenReturn("kc-003");
        when(keycloakAuthService.realmRoleExists("EMPLOYEE")).thenReturn(true);
        when(keycloakUserOnboardingTransactionService.synchronizeAndIssueActivationToken("kc-003"))
                .thenReturn(new KeycloakUserOnboardingTransactionService.OnboardingResult(syncedUser, token));

        User createdUser = service.createUser(request);

        assertSame(syncedUser, createdUser);
        verify(keycloakAuthService).setRealmRoles("kc-003", Set.of("EMPLOYEE"));
        verify(keycloakAuthService).assignUserToManagedGroup("kc-003", "employees", Set.of("employees", "managers", "hr", "admins"));
        verify(keycloakAuthService, never()).deleteUser("kc-003");
    }
}
