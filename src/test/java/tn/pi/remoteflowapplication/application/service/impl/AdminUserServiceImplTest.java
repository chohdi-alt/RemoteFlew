package tn.pi.remoteflowapplication.application.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.AccountActivationService;
import tn.pi.remoteflowapplication.application.service.KeycloakUserService;
import tn.pi.remoteflowapplication.application.service.LoginProtectionService;
import tn.pi.remoteflowapplication.application.service.TeamService;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private KeycloakUserService keycloakUserService;

    @Mock
    private AccountActivationService accountActivationService;

    @Mock
    private LoginProtectionService loginProtectionService;

    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminUserServiceImpl(
                keycloakAuthService,
                userRepository,
                teamService,
                keycloakUserService,
                accountActivationService,
                loginProtectionService);
    }

    @Test
    void shouldTriggerResetLinkWhenAdminReactivatesUser() {
        User user = new User("kc-1", "alice@mail.local", "Doe", "Alice", "alice", false);
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateActivation("kc-1", true);

        verify(keycloakAuthService).setEnabled("kc-1", true);
        ArgumentCaptor<String> temporaryPasswordCaptor = ArgumentCaptor.forClass(String.class);
        verify(keycloakAuthService).setTemporaryPassword(eq("kc-1"), temporaryPasswordCaptor.capture());
        verify(keycloakAuthService).addRequiredAction("kc-1", "UPDATE_PASSWORD");
        verify(accountActivationService).issueTokenAndDispatch(user, "ADMIN_REACTIVATION");
        verify(loginProtectionService).clearTracking("alice");
        assertTrue(temporaryPasswordCaptor.getValue() != null && temporaryPasswordCaptor.getValue().length() > 8);
    }

    @Test
    void shouldNotTriggerResetLinkWhenAdminDeactivatesUser() {
        User user = new User("kc-2", "bob@mail.local", "Bob", "Builder", "bob", true);
        when(userRepository.findByKeycloakId("kc-2")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateActivation("kc-2", false);

        verify(keycloakAuthService).setEnabled("kc-2", false);
        verify(keycloakAuthService, never()).setTemporaryPassword(eq("kc-2"), anyString());
        verify(accountActivationService, never()).issueTokenAndDispatch(user, "ADMIN_REACTIVATION");
        verify(loginProtectionService).clearTracking("bob");
    }
}
