package tn.pi.remoteflowapplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class ControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private CreateTeleworkRequestHandler createTeleworkRequestHandler;

    @MockBean
    private ManagerApprovalHandler managerApprovalHandler;

    @MockBean
    private HrApprovalHandler hrApprovalHandler;

    @MockBean
    private TeleworkStatusQueryService teleworkStatusQueryService;

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .build();
        }
    }

    @Test
    void employeeCanCreateRequests() throws Exception {
        when(createTeleworkRequestHandler.handle(any(), any())).thenReturn(1L);

        String json = """
                {
                  "employeeId": "emp-1",
                  "startDate": "2026-02-02",
                  "endDate": "2026-02-02",
                  "reason": "reason"
                }
                """;

        MockMultipartFile data = new MockMultipartFile(
                "data",
                "data.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "justif.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "x".getBytes()
        );

        mockMvc.perform(multipart("/api/telework")
                        .file(data)
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("emp-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void managerCannotCreateRequests() throws Exception {
        String json = """
                {
                  "employeeId": "emp-1",
                  "startDate": "2026-02-02",
                  "endDate": "2026-02-02",
                  "reason": "reason"
                }
                """;

        MockMultipartFile data = new MockMultipartFile(
                "data",
                "data.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "justif.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "x".getBytes()
        );

        mockMvc.perform(multipart("/api/telework")
                        .file(data)
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("manager-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCannotApproveManagerEndpoints() throws Exception {
        String body = """
                {
                  "requestId": 1,
                  "managerId": "hr-1",
                  "comment": "ok"
                }
                """;

        mockMvc.perform(post("/api/telework/1/manager/approve")
                        .param("taskKey", "123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(jwt -> jwt.subject("hr-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_HR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthorizedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/employee/telework"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbiddenAccessReturns403() throws Exception {
        mockMvc.perform(get("/api/employee/telework")
                        .with(jwt().jwt(jwt -> jwt.subject("manager-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtRoleMappingUsesRealmAccess() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .claim("realm_access", Map.of("roles", List.of("EMPLOYEE", "HR")))
                .build();

        var auth = jwtAuthenticationConverter.convert(jwt);

        List<String> authorities = auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        assertTrue(authorities.contains("ROLE_EMPLOYEE"));
        assertTrue(authorities.contains("ROLE_HR"));
    }
}
