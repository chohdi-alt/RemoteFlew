package tn.pi.remoteflowapplication.infrastructure.security;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.domain.exception.ExternalServiceException;
import tn.pi.remoteflowapplication.domain.exception.KeycloakConflictException;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakAuthServiceTest {

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private Response response;

    private KeycloakAuthService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakAuthService(keycloak, "pfe-realm");
        when(keycloak.realm("pfe-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void shouldMapKeycloak409ToConflictException() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(409);

        assertThrows(KeycloakConflictException.class,
                () -> service.createUser("admin01", "admin01@mailhog.local", "Admin", "One"));
    }

    @Test
    void shouldMapUnexpectedStatusToExternalServiceException() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(503);
        when(response.getStatusInfo()).thenReturn(Response.Status.SERVICE_UNAVAILABLE);
        when(response.readEntity(String.class)).thenReturn("{\"error\":\"service_unavailable\"}");
        when(response.hasEntity()).thenReturn(true);

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> service.createUser("new-user", "new-user@mailhog.local", "New", "User"));
        assertEquals(503, exception.getUpstreamStatus());
    }

    @Test
    void shouldResolveCreatedUserIdWhenLocationHeaderIsMissing() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(null);

        UserRepresentation representation = new UserRepresentation();
        representation.setUsername("new-user");
        representation.setId("resolved-id");
        when(usersResource.searchByUsername("new-user", true)).thenReturn(List.of(representation));

        String createdId = service.createUser("new-user", "new-user@mailhog.local", "New", "User");

        assertEquals("resolved-id", createdId);
    }

    @Test
    void shouldMapWebApplicationFailureWithStatusAndBodyForUsernameLookup() {
        Response forbiddenResponse = Response.status(403)
                .entity("{\"error\":\"insufficient_scope\"}")
                .build();
        when(usersResource.searchByUsername("new-user", true))
                .thenThrow(new jakarta.ws.rs.ForbiddenException(forbiddenResponse));

        ExternalServiceException exception = assertThrows(
                ExternalServiceException.class,
                () -> service.findUserIdByUsername("new-user"));

        assertEquals(403, exception.getUpstreamStatus());
        assertEquals("search user by username 'new-user'", exception.getOperation());
        assertEquals("/admin/realms/pfe-realm/users?username=new-user&exact=true", exception.getUpstreamUrl());
    }

    @Test
    void shouldClassifyWrongRealmOrEndpointAs404() {
        Response notFoundResponse = Response.status(404)
                .entity("{\"error\":\"Realm not found\"}")
                .build();
        when(usersResource.searchByEmail("ghost@mailhog.local", true))
                .thenThrow(new jakarta.ws.rs.NotFoundException(notFoundResponse));

        ExternalServiceException exception = assertThrows(
                ExternalServiceException.class,
                () -> service.findUserIdByEmail("ghost@mailhog.local"));

        assertEquals(404, exception.getUpstreamStatus());
        assertEquals("search user by email 'ghost@mailhog.local'", exception.getOperation());
    }

    @Test
    void shouldClassifyNetworkFailureAsExternalServiceException() {
        when(usersResource.searchByEmail("netfail@mailhog.local", true))
                .thenThrow(new jakarta.ws.rs.ProcessingException(new java.net.ConnectException("Connection refused")));

        ExternalServiceException exception = assertThrows(
                ExternalServiceException.class,
                () -> service.findUserIdByEmail("netfail@mailhog.local"));

        assertNull(exception.getUpstreamStatus());
        assertEquals("/admin/realms/pfe-realm/users?email=netfail@mailhog.local&exact=true", exception.getUpstreamUrl());
    }

    @Test
    void shouldReturnCreatedUserIdOn201() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://localhost:8081/admin/realms/pfe-realm/users/abc-123"));

        String createdId = service.createUser("new-user", "new-user@mailhog.local", "New", "User");

        assertEquals("abc-123", createdId);
    }

    @Test
    void shouldCreateEnabledUserWithUpdatePasswordRequiredAction() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://localhost:8081/admin/realms/pfe-realm/users/created-456"));

        service.createUser("enabled.user", "enabled.user@mailhog.local", "Enabled", "User");

        ArgumentCaptor<UserRepresentation> representationCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(representationCaptor.capture());
        UserRepresentation captured = representationCaptor.getValue();

        assertEquals(Boolean.TRUE, captured.isEnabled());
        assertTrue(captured.getRequiredActions().contains("UPDATE_PASSWORD"));
    }
}
