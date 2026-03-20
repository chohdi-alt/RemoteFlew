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
import tn.pi.remoteflowapplication.application.dto.AdminDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.EmployeeDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.MonthlyCountDTO;
import tn.pi.remoteflowapplication.application.dto.AuthTokenResponse;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;
import tn.pi.remoteflowapplication.application.service.DashboardService;
import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakTokenService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "alfresco.telework-folder-id=test",
                "alfresco.pending-folder-id=test",
                "alfresco.approved-folder-id=test",
                "alfresco.rejected-folder-id=test"
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

        @MockBean
        private KeycloakTokenService keycloakTokenService;

        @MockBean
        private DashboardService dashboardService;

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
                                json.getBytes());
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "justif.txt",
                                MediaType.TEXT_PLAIN_VALUE,
                                "x".getBytes());

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
                                json.getBytes());
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "justif.txt",
                                MediaType.TEXT_PLAIN_VALUE,
                                "x".getBytes());

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
        void authLoginEndpointIsPublic() throws Exception {
                when(keycloakTokenService.login(anyString(), anyString()))
                                .thenReturn(new AuthTokenResponse(
                                                "access-token",
                                                "refresh-token",
                                                "Bearer",
                                                300L,
                                                1800L,
                                                List.of("ROLE_EMPLOYEE")));

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "username": "employee-1",
                                                  "password": "password"
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").value("access-token"))
                                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.expiresIn").value(300))
                                .andExpect(jsonPath("$.refreshExpiresIn").value(1800))
                                .andExpect(jsonPath("$.roles[0]").value("ROLE_EMPLOYEE"));
        }

        @Test
        void authRefreshEndpointIsPublic() throws Exception {
                when(keycloakTokenService.refresh(anyString()))
                                .thenReturn(new AuthTokenResponse(
                                                "new-access-token",
                                                "new-refresh-token",
                                                "Bearer",
                                                300L,
                                                1800L,
                                                List.of("ROLE_EMPLOYEE")));

                mockMvc.perform(post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "refreshToken": "refresh-token"
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.expiresIn").value(300))
                                .andExpect(jsonPath("$.refreshExpiresIn").value(1800))
                                .andExpect(jsonPath("$.roles[0]").value("ROLE_EMPLOYEE"));
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

        @Test
        void employeeHistoryForbiddenWhenOwnershipFails() throws Exception {
                when(teleworkStatusQueryService.getRequestHistory(eq(99L), any()))
                                .thenThrow(new ForbiddenOperationException("forbidden"));

                mockMvc.perform(get("/api/telework/99/history")
                                .with(jwt().jwt(jwt -> jwt.subject("emp-2"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                                .andExpect(status().isForbidden());
        }

        @Test
        void adminCanAccessAdminDashboard() throws Exception {
                when(dashboardService.getAdminDashboard(any(), any()))
                                .thenReturn(new AdminDashboardDTO(
                                                Map.of(RequestStatus.SUBMITTED, 1L),
                                                10.0d,
                                                5.0d,
                                                List.of(new MonthlyCountDTO(2026, 2, 1L)),
                                                2L,
                                                1L,
                                                120.0d));

                mockMvc.perform(get("/api/dashboard/admin")
                                .with(jwt().jwt(jwt -> jwt.subject("admin-1"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.pendingManagerTasks").value(2))
                                .andExpect(jsonPath("$.pendingHrTasks").value(1));
        }

        @Test
        void employeeCannotAccessAdminDashboard() throws Exception {
                mockMvc.perform(get("/api/dashboard/admin")
                                .with(jwt().jwt(jwt -> jwt.subject("emp-1"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                                .andExpect(status().isForbidden());
        }

        @Test
        void employeeCanAccessEmployeeDashboard() throws Exception {
                when(dashboardService.getEmployeeDashboard(anyString(), any(), any()))
                                .thenReturn(new EmployeeDashboardDTO(
                                                Map.of(RequestStatus.SUBMITTED, 2L),
                                                List.of(new MonthlyCountDTO(2026, 2, 2L)),
                                                List.of()));

                mockMvc.perform(get("/api/dashboard/employee")
                                .with(jwt().jwt(jwt -> jwt.subject("emp-1"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.myTotalsByStatus.SUBMITTED").value(2));
        }
}
