package tn.pi.remoteflowapplication.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.pi.remoteflowapplication.application.port.out.RoleRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.Role;
import tn.pi.remoteflowapplication.domain.entity.User;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class CurrentUserResolverServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TeleworkRequestRepository teleworkRequestRepository;

    @InjectMocks
    private CurrentUserResolverService service;

    @Test
    void resolvesExistingUserByKeycloakIdAndSynchronizesProfile() {
        User existing = new User("kc-1", "old-user", "Old Name", "old@example.com");
        Role employeeRole = new Role("EMPLOYEE");
        existing.addRole(employeeRole);

        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.save(existing)).thenReturn(existing);

        Jwt jwt = jwt("kc-1", "new-user", "new@example.com", "New Name", List.of("EMPLOYEE"));

        User resolved = service.resolveCurrentUser(jwt);

        assertEquals("kc-1", resolved.getKeycloakId());
        assertEquals("new-user", resolved.getUsername());
        assertEquals("new@example.com", resolved.getEmail());
        assertEquals("New Name", resolved.getFullName());
        verify(teleworkRequestRepository).reassignEmployeeId("old-user", "new-user");
    }

    @Test
    void autoCreatesUserWhenNotFound() {
        Role employeeRole = new Role("EMPLOYEE");

        when(userRepository.findByKeycloakId("kc-2")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("employee-two")).thenReturn(Optional.empty());
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Jwt jwt = jwt("kc-2", "employee-two", "employee-two@example.com", "Employee Two", List.of());

        User resolved = service.resolveCurrentUser(jwt);

        assertEquals("kc-2", resolved.getKeycloakId());
        assertEquals("employee-two", resolved.getUsername());
        assertEquals("employee-two@example.com", resolved.getEmail());
        assertTrue(resolved.getRoles().stream().anyMatch(r -> "EMPLOYEE".equals(r.getName())));
        verify(teleworkRequestRepository, never()).reassignEmployeeId(any(), any());
    }

    @Test
    void updatesUsernameWhenChangedInKeycloak() {
        User existing = new User("kc-3", "legacy-user", "Legacy Name", "legacy@example.com");
        Role managerRole = new Role("MANAGER");
        when(userRepository.findByKeycloakId("kc-3")).thenReturn(Optional.of(existing));
        when(roleRepository.findByName("MANAGER")).thenReturn(Optional.of(managerRole));
        when(userRepository.save(existing)).thenReturn(existing);

        Jwt jwt = jwt("kc-3", "manager-renamed", "manager@example.com", "Manager Renamed", List.of("MANAGER"));

        User resolved = service.resolveCurrentUser(jwt);

        assertEquals("manager-renamed", resolved.getUsername());
        assertEquals("manager@example.com", resolved.getEmail());
        verify(teleworkRequestRepository).reassignEmployeeId("legacy-user", "manager-renamed");
    }

    @Test
    void healsLegacyLookupByUsernameAndSetsKeycloakId() {
        User existing = new User("legacy-sub", "employee-4", "Employee Four", "employee4@example.com");
        Role employeeRole = new Role("EMPLOYEE");

        when(userRepository.findByKeycloakId("kc-4")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("employee-4")).thenReturn(Optional.of(existing));
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.save(existing)).thenReturn(existing);

        Jwt jwt = jwt("kc-4", "employee-4", "employee4@example.com", "Employee Four", List.of("EMPLOYEE"));

        User resolved = service.resolveCurrentUser(jwt);

        assertEquals("kc-4", resolved.getKeycloakId());
        assertEquals("employee-4", resolved.getUsername());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertNotNull(savedUser.getValue());
        assertEquals("kc-4", savedUser.getValue().getKeycloakId());
        verify(teleworkRequestRepository).reassignEmployeeId(eq("employee-4"), eq("employee-4"));
    }

    private Jwt jwt(
            String sub,
            String preferredUsername,
            String email,
            String name,
            List<String> realmRoles) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", sub);
        claims.put("preferred_username", preferredUsername);
        claims.put("email", email);
        claims.put("name", name);
        claims.put("realm_access", Map.of("roles", realmRoles));

        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(600),
                Map.of("alg", "none"),
                claims);
    }
}
