package tn.pi.remoteflowapplication.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import tn.pi.remoteflowapplication.domain.exception.AuthErrorCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakTokenServiceAuthErrorCodeTest {

        @Mock
        private KeycloakAuthService keycloakAuthService;

        private KeycloakTokenService keycloakTokenService;
        private Method resolveAuthErrorCodeMethod;
        private Constructor<?> keycloakErrorConstructor;
        private Constructor<?> failureAnalysisConstructor;

        @BeforeEach
        void setUp() throws Exception {
                keycloakTokenService = new KeycloakTokenService(WebClient.builder().build(), new ObjectMapper(),
                                keycloakAuthService, null, null);

                Class<?> keycloakErrorClass = Class.forName(
                                "tn.pi.remoteflowapplication.infrastructure.security.KeycloakTokenService$KeycloakError");
                keycloakErrorConstructor = keycloakErrorClass.getDeclaredConstructor(String.class, String.class);
                keycloakErrorConstructor.setAccessible(true);

                Class<?> failureAnalysisClass = Class.forName(
                                "tn.pi.remoteflowapplication.infrastructure.security.KeycloakTokenService$FailureAnalysis");
                failureAnalysisConstructor = failureAnalysisClass.getDeclaredConstructor(String.class, String.class);
                failureAnalysisConstructor.setAccessible(true);

                resolveAuthErrorCodeMethod = KeycloakTokenService.class.getDeclaredMethod(
                                "resolveAuthErrorCode",
                                String.class,
                                String.class,
                                int.class,
                                keycloakErrorClass,
                                failureAnalysisClass);
                resolveAuthErrorCodeMethod.setAccessible(true);
        }

        @Test
        void shouldReturnAuthInvalidForWrongCredentials() throws Exception {
                when(keycloakAuthService.findUserIdByUsername("alice")).thenReturn(Optional.empty());

                AuthErrorCode code = invokeResolve("password", "alice", 401, "invalid_grant",
                                "Invalid user credentials",
                                "a) Wrong password");

                assertEquals(AuthErrorCode.AUTH_INVALID, code);
        }

        @Test
        void shouldReturnAuthTempLockWhenUserIsTemporarilyLocked() throws Exception {
                when(keycloakAuthService.findUserIdByUsername("alice")).thenReturn(Optional.of("uid-1"));
                when(keycloakAuthService.isUserEnabled("uid-1")).thenReturn(true);
                when(keycloakAuthService.isUserTemporarilyLocked("uid-1")).thenReturn(true);

                AuthErrorCode code = invokeResolve("password", "alice", 401, "invalid_grant",
                                "Invalid user credentials",
                                "unknown");

                assertEquals(AuthErrorCode.AUTH_TEMP_LOCK, code);
        }

        @Test
        void shouldReturnAuthAccountDisabledWhenUserIsDisabled() throws Exception {
                when(keycloakAuthService.findUserIdByUsername("alice")).thenReturn(Optional.of("uid-2"));
                when(keycloakAuthService.isUserEnabled("uid-2")).thenReturn(false);

                AuthErrorCode code = invokeResolve("password", "alice", 401, "invalid_grant",
                                "Invalid user credentials",
                                "unknown");

                assertEquals(AuthErrorCode.AUTH_ACCOUNT_DISABLED, code);
        }

        @Test
        void shouldReturnPasswordUpdateRequiredWhenRequiredActionIsDetected() throws Exception {
                AuthErrorCode code = invokeResolve("password", "alice", 401, "invalid_grant",
                                "Account is not fully set up",
                                "c) Required action pending");

                assertEquals(AuthErrorCode.PASSWORD_UPDATE_REQUIRED, code);
        }

        private AuthErrorCode invokeResolve(
                        String grantType,
                        String username,
                        int status,
                        String keycloakError,
                        String keycloakDescription,
                        String classification) throws Exception {
                Object error = keycloakErrorConstructor.newInstance(keycloakError, keycloakDescription);
                Object analysis = failureAnalysisConstructor.newInstance(classification, "n/a");
                return (AuthErrorCode) resolveAuthErrorCodeMethod.invoke(
                                keycloakTokenService,
                                grantType,
                                username,
                                status,
                                error,
                                analysis);
        }
}
