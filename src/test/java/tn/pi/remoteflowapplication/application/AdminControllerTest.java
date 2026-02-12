package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.controller.AdminController;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminController adminController;

    @Test
    void syncUsersSavesNewUsers() {
        UserRepresentation kr = new UserRepresentation();
        kr.setId("key-1");
        kr.setFirstName("John");
        kr.setLastName("Doe");
        kr.setEmail("john@example.com");

        when(keycloakAuthService.getAllUsers()).thenReturn(List.of(kr));
        when(userRepository.findByExternalId("key-1")).thenReturn(Optional.empty());

        adminController.syncUsersFromKeycloak();

        verify(userRepository).save(any());
    }

    @Test
    void syncUsersSkipsExistingUsers() {
        UserRepresentation kr = new UserRepresentation();
        kr.setId("key-1");

        when(keycloakAuthService.getAllUsers()).thenReturn(List.of(kr));
        when(userRepository.findByExternalId("key-1"))
                .thenReturn(Optional.of(mock(tn.pi.remoteflowapplication.domain.entity.User.class)));

        adminController.syncUsersFromKeycloak();

        verify(userRepository, never()).save(any());
    }
}
