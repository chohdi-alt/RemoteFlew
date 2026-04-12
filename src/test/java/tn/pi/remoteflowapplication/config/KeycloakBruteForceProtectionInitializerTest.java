package tn.pi.remoteflowapplication.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakBruteForceProtectionInitializerTest {

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    private LoginProtectionProperties properties;
    private KeycloakBruteForceProtectionInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new LoginProtectionProperties();
        initializer = new KeycloakBruteForceProtectionInitializer(keycloak, "pfe-realm", properties);
    }

    @Test
    void shouldSkipWhenBootstrapIsDisabled() throws Exception {
        properties.setBootstrapKeycloakBruteForce(false);

        initializer.run(new DefaultApplicationArguments());

        verify(realmResource, never()).toRepresentation();
        verify(realmResource, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldApplyTargetConfigurationWhenRealmDiffers() throws Exception {
        when(keycloak.realm("pfe-realm")).thenReturn(realmResource);

        RealmRepresentation representation = new RealmRepresentation();
        representation.setBruteForceProtected(false);
        representation.setFailureFactor(3);
        representation.setWaitIncrementSeconds(30);
        representation.setQuickLoginCheckMilliSeconds(500L);
        representation.setMinimumQuickLoginWaitSeconds(15);
        representation.setMaxFailureWaitSeconds(120);
        representation.setMaxDeltaTimeSeconds(300);
        representation.setPermanentLockout(true);
        when(realmResource.toRepresentation()).thenReturn(representation);

        initializer.run(new DefaultApplicationArguments());

        verify(realmResource).update(representation);
        assertTrue(Boolean.TRUE.equals(representation.isBruteForceProtected()));
        assertEquals(5, representation.getFailureFactor());
        assertEquals(60, representation.getWaitIncrementSeconds());
        assertEquals(1000L, representation.getQuickLoginCheckMilliSeconds());
        assertEquals(60, representation.getMinimumQuickLoginWaitSeconds());
        assertEquals(300, representation.getMaxFailureWaitSeconds());
        assertEquals(900, representation.getMaxDeltaTimeSeconds());
        assertFalse(Boolean.TRUE.equals(representation.isPermanentLockout()));
    }

    @Test
    void shouldNotUpdateRealmWhenAlreadyCompliant() throws Exception {
        when(keycloak.realm("pfe-realm")).thenReturn(realmResource);

        RealmRepresentation representation = new RealmRepresentation();
        representation.setBruteForceProtected(true);
        representation.setFailureFactor(5);
        representation.setWaitIncrementSeconds(60);
        representation.setQuickLoginCheckMilliSeconds(1000L);
        representation.setMinimumQuickLoginWaitSeconds(60);
        representation.setMaxFailureWaitSeconds(300);
        representation.setMaxDeltaTimeSeconds(900);
        representation.setPermanentLockout(false);
        when(realmResource.toRepresentation()).thenReturn(representation);

        initializer.run(new DefaultApplicationArguments());

        verify(realmResource, never()).update(representation);
    }
}
