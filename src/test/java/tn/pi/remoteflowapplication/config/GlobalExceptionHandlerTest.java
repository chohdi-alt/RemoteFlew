package tn.pi.remoteflowapplication.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.domain.exception.ExternalServiceException;
import tn.pi.remoteflowapplication.domain.exception.KeycloakConflictException;
import tn.pi.remoteflowapplication.domain.exception.UserAlreadyExistsException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn409ForKeycloakConflictException() throws Exception {
                mockMvc.perform(get("/test/errors/keycloak-conflict")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("KEYCLOAK_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Email already exists in Keycloak."))
                .andExpect(jsonPath("$.path").value("/test/errors/keycloak-conflict"));
    }

    @Test
    void shouldReturn409ForUserAlreadyExistsException() throws Exception {
                mockMvc.perform(get("/test/errors/user-exists")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("User already exists."));
    }

    @Test
    void shouldReturn502ForExternalServiceException() throws Exception {
                mockMvc.perform(get("/test/errors/external-service")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("EXTERNAL_SERVICE_ERROR"))
                .andExpect(jsonPath("$.message").value("Keycloak unavailable."));
    }

    @RestController
    static class ErrorThrowingController {

        @GetMapping("/test/errors/keycloak-conflict")
        void keycloakConflict() {
            throw new KeycloakConflictException("Email already exists in Keycloak.");
        }

        @GetMapping("/test/errors/user-exists")
        void userExists() {
            throw new UserAlreadyExistsException("User already exists.");
        }

        @GetMapping("/test/errors/external-service")
        void externalService() {
            throw new ExternalServiceException("Keycloak unavailable.");
        }
    }
}
