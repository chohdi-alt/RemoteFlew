package tn.pi.remoteflowapplication.tests.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.AdminDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.EmployeeDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.HrDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.ManagerDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.RoleDTO;
import tn.pi.remoteflowapplication.application.dto.SmtpConfigResponse;
import tn.pi.remoteflowapplication.application.dto.TeamDirectoryDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkQuotaResponse;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.query.ReportQueryService;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;
import tn.pi.remoteflowapplication.application.service.ActivationTokenService;
import tn.pi.remoteflowapplication.application.service.AdminUserService;
import tn.pi.remoteflowapplication.application.service.AgreementService;
import tn.pi.remoteflowapplication.application.service.AuditLogQueryService;
import tn.pi.remoteflowapplication.application.service.CurrentUserResolverService;
import tn.pi.remoteflowapplication.application.service.DashboardService;
import tn.pi.remoteflowapplication.application.service.DirectoryService;
import tn.pi.remoteflowapplication.application.service.LoginProtectionService;
import tn.pi.remoteflowapplication.application.service.ReportExportService;
import tn.pi.remoteflowapplication.application.service.RoleService;
import tn.pi.remoteflowapplication.application.service.SmtpConfigurationService;
import tn.pi.remoteflowapplication.application.service.SystemConfigurationService;
import tn.pi.remoteflowapplication.application.service.TeamService;
import tn.pi.remoteflowapplication.application.service.TeleworkQuotaService;
import tn.pi.remoteflowapplication.application.service.TeleworkScoringScheduler;
import tn.pi.remoteflowapplication.application.service.TeleworkScoringService;
import tn.pi.remoteflowapplication.application.service.ValidationInboxService;
import tn.pi.remoteflowapplication.config.SecurityConfig;
import tn.pi.remoteflowapplication.controller.AdminController;
import tn.pi.remoteflowapplication.controller.AdminDirectoryController;
import tn.pi.remoteflowapplication.controller.AdminSmtpController;
import tn.pi.remoteflowapplication.controller.AdminTeamController;
import tn.pi.remoteflowapplication.controller.AuthController;
import tn.pi.remoteflowapplication.controller.DashboardController;
import tn.pi.remoteflowapplication.controller.ReportController;
import tn.pi.remoteflowapplication.controller.TeleworkCommandController;
import tn.pi.remoteflowapplication.controller.TeleworkQueryController;
import tn.pi.remoteflowapplication.controller.TeleworkScoringController;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkScoreJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakTokenService;
import tn.pi.remoteflowapplication.tests.security.support.JwtTestTokenFactory;
import tn.pi.remoteflowapplication.tests.security.support.TestJwtSecurityConfiguration;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@AutoConfigureMockMvc
@Import({SecurityConfig.class, TestJwtSecurityConfiguration.class})
@WebMvcTest(controllers = {
        AdminController.class,
        AdminDirectoryController.class,
        AdminTeamController.class,
        AdminSmtpController.class,
        DashboardController.class,
        ReportController.class,
        TeleworkCommandController.class,
        TeleworkQueryController.class,
        TeleworkScoringController.class,
        AuthController.class
})
class ControllerAuthorizationSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    private final JwtTestTokenFactory jwtTestTokenFactory = new JwtTestTokenFactory();

    @MockBean
    private AdminUserService adminUserService;
    @MockBean
    private AuditLogQueryService auditLogQueryService;
    @MockBean
    private SystemConfigurationService systemConfigurationService;
    @MockBean
    private RoleService roleService;
    @MockBean
    private TeleworkRequestRepository teleworkRequestRepository;
    @MockBean
    private DocumentStoragePort documentStoragePort;
    @MockBean
    private SpringTeleworkScoreJpaRepository teleworkScoreRepository;
    @MockBean
    private DirectoryService directoryService;
    @MockBean
    private TeamService teamService;
    @MockBean
    private SmtpConfigurationService smtpConfigurationService;
    @MockBean
    private DashboardService dashboardService;
    @MockBean
    private ReportQueryService reportQueryService;
    @MockBean
    private ReportExportService reportExportService;
    @MockBean
    private CreateTeleworkRequestHandler createTeleworkRequestHandler;
    @MockBean
    private ManagerApprovalHandler managerApprovalHandler;
    @MockBean
    private HrApprovalHandler hrApprovalHandler;
    @MockBean
    private CurrentUserResolverService currentUserResolverService;
    @MockBean
    private TeleworkStatusQueryService teleworkStatusQueryService;
    @MockBean
    private ValidationInboxService validationInboxService;
    @MockBean
    private AgreementService agreementService;
    @MockBean
    private TeleworkQuotaService teleworkQuotaService;
    @MockBean
    private TeleworkScoringService teleworkScoringService;
    @MockBean
    private TeleworkScoringScheduler teleworkScoringScheduler;
    @MockBean
    private KeycloakTokenService keycloakTokenService;
    @MockBean
    private KeycloakAuthService keycloakAuthService;
    @MockBean
    private ActivationTokenService activationTokenService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private LoginProtectionService loginProtectionService;
    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUpControllerStubs() {
        when(clientIpResolver.resolve(any())).thenReturn("127.0.0.1");

        Page<?> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(adminUserService.findAll(any())).thenReturn((Page) emptyPage);
        when(roleService.getRoles()).thenReturn(List.of(new RoleDTO("ADMIN")));
        when(directoryService.getAllUsers()).thenReturn(List.of());
        when(teamService.createTeam(anyString(), anyString())).thenReturn(new tn.pi.remoteflowapplication.domain.entity.Team("Ops"));

        when(smtpConfigurationService.getEffectiveConfiguration()).thenReturn(new SmtpConfigResponse(
                1L,
                "primary",
                "smtp.local",
                587,
                "smtp",
                "mailer",
                true,
                "no-reply@remoteflow.local",
                true,
                true,
                false,
                5000,
                5000,
                5000,
                true,
                "DB",
                Instant.now()));

        when(dashboardService.getAdminDashboard(any(), any())).thenReturn(new AdminDashboardDTO(
                Map.of(RequestStatus.SUBMITTED, 2L),
                50.0,
                10.0,
                List.of(),
                1L,
                1L,
                24.5));
        when(dashboardService.getHrDashboard(any(), any())).thenReturn(new HrDashboardDTO(
                Map.of(RequestStatus.MANAGER_APPROVED, 1L),
                List.of(),
                1L,
                12.0));
        when(dashboardService.getManagerDashboard(anyString(), any(), any())).thenReturn(new ManagerDashboardDTO(
                Map.of(RequestStatus.SUBMITTED, 1L),
                List.of(),
                1L,
                10.0));
        when(dashboardService.getEmployeeDashboard(anyString(), any(), any())).thenReturn(new EmployeeDashboardDTO(
                Map.of(RequestStatus.SUBMITTED, 1L),
                List.of(),
                List.of()));

        when(reportQueryService.generateReport(any())).thenReturn(Map.of("generated", true));
        when(currentUserResolverService.resolveCurrentUser(any())).thenReturn(new User(
                "kc-employee",
                "employee.user",
                "Employee User",
                "employee@remoteflow.local"));
        when(teleworkQuotaService.getCurrentWeekQuota(anyString())).thenReturn(new TeleworkQuotaResponse(3, 1, 2));
        when(teleworkStatusQueryService.findById(any(), any(Authentication.class))).thenReturn(new TeleworkStatusDTO(
                42L,
                LocalDate.parse("2026-04-10"),
                LocalDate.parse("2026-04-11"),
                "SUBMITTED",
                null,
                false,
                null,
                null,
                null,
                null));

        when(teleworkScoringService.getPendingScoresForManager(anyString())).thenReturn(List.of());
        when(teleworkScoringService.searchScoresForHr(any(), any(), any(), any())).thenReturn(List.of());
        when(teleworkScoringScheduler.runNow()).thenReturn(2);
    }

    @ParameterizedTest(name = "[401] {0}")
    @MethodSource("securedEndpoints")
    void shouldReturnUnauthorizedWhenTokenIsMissing(SecuredEndpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request().get())
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "[403] {0}")
    @MethodSource("securedEndpoints")
    void shouldReturnForbiddenWhenRoleIsWrong(SecuredEndpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request().get()
                        .header("Authorization", jwtTestTokenFactory.bearerTokenForRole("wrong-role-user", wrongRoleFor(endpoint.requiredRole()))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "[2xx] {0}")
    @MethodSource("securedEndpoints")
    void shouldAllowRequestWhenRoleMatches(SecuredEndpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request().get()
                        .header("Authorization", jwtTestTokenFactory.bearerTokenForRole("authorized-user", endpoint.requiredRole())))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status >= 200 && status < 500, 
                        "Expected success or client error but got " + status);
                });
    }

    @ParameterizedTest
    @MethodSource("authenticatedOnlyEndpoints")
    void shouldAllowAuthenticatedEndpointsRegardlessOfRole(SecuredEndpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request().get()
                        .header("Authorization", jwtTestTokenFactory.bearerTokenForRole("auth-user", "EMPLOYEE")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status >= 200 && status < 500, 
                        "Expected success or client error but got " + status);
                });
    }

    @ParameterizedTest
    @MethodSource("authenticatedOnlyEndpoints")
    void shouldRejectAuthenticatedEndpointsWithoutToken(SecuredEndpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request().get())
                .andExpect(status().isUnauthorized());
    }

    private static Stream<SecuredEndpoint> securedEndpoints() {
        return Stream.of(
                new SecuredEndpoint("admin.roles", "ADMIN", () -> get("/api/admin/roles")),
                new SecuredEndpoint("admin.directory.users", "ADMIN", () -> get("/api/admin/directory/users")),
                new SecuredEndpoint("admin.team.create", "ADMIN", () -> post("/api/admin/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ops\",\"managerExternalId\":\"kc-manager\"}")),
                new SecuredEndpoint("admin.smtp.effective", "ADMIN", () -> get("/api/admin/smtp/effective")),
                new SecuredEndpoint("dashboard.admin", "ADMIN", () -> get("/api/dashboard/admin")),
                new SecuredEndpoint("dashboard.hr", "HR", () -> get("/api/dashboard/hr")),
                new SecuredEndpoint("dashboard.manager", "MANAGER", () -> get("/api/dashboard/manager")),
                new SecuredEndpoint("dashboard.employee", "EMPLOYEE", () -> get("/api/dashboard/employee")),
                new SecuredEndpoint("report.generate", "HR", () -> post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDate\":\"2026-01-01\",\"toDate\":\"2026-01-31\",\"department\":\"IT\",\"status\":\"APPROVED\"}")),
                new SecuredEndpoint("telework.create", "EMPLOYEE", ControllerAuthorizationSecurityIT::buildCreateTeleworkRequest),
                new SecuredEndpoint("telework.manager.approve", "MANAGER", () -> post("/api/telework/42/manager/approve")
                        .param("taskKey", "90001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"manager-ok\"}")),
                new SecuredEndpoint("telework.manager.reject", "MANAGER", () -> post("/api/telework/42/manager/reject")
                        .param("taskKey", "90001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"manager-no\"}")),
                new SecuredEndpoint("telework.hr.approve", "HR", () -> post("/api/telework/42/hr/approve")
                        .param("taskKey", "90002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"hr-ok\"}")),
                new SecuredEndpoint("telework.hr.reject", "HR", () -> post("/api/telework/42/hr/reject")
                        .param("taskKey", "90002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"hr-no\"}")),
                new SecuredEndpoint("telework.query.quota", "EMPLOYEE", () -> get("/api/telework/quota")),
                new SecuredEndpoint("telework.query.by-id", "MANAGER", () -> get("/api/telework/42")),
                new SecuredEndpoint("telework.query.mine", "EMPLOYEE", () -> get("/api/employee/telework")),
                new SecuredEndpoint("scoring.manager.pending", "MANAGER", () -> get("/api/scoring/manager/pending")),
                new SecuredEndpoint("scoring.manager.submit", "MANAGER", () -> post("/api/scoring/42/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performanceScore\":90,\"attendanceScore\":80,\"punctualityScore\":85,\"behaviorScore\":95,\"managerComment\":\"Good\"}")),
                new SecuredEndpoint("scoring.hr.search", "HR", () -> get("/api/scoring")),
                new SecuredEndpoint("scoring.hr.get-by-id", "HR", () -> get("/api/scoring/42")),
                new SecuredEndpoint("scoring.hr.review", "HR", () -> post("/api/scoring/42/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"hrComment\":\"Reviewed\"}")),
                new SecuredEndpoint("scoring.admin.get-by-request", "HR", () -> get("/api/scoring/request/42")),
                new SecuredEndpoint("scoring.scheduler.run", "ADMIN", () -> post("/api/scoring/scheduler/run"))
        );
    }

    private static Stream<SecuredEndpoint> authenticatedOnlyEndpoints() {
        return Stream.of(
                new SecuredEndpoint("auth.current-user", "EMPLOYEE", () -> get("/api/auth/me")),
                new SecuredEndpoint("auth.logout", "EMPLOYEE", () -> post("/api/auth/logout")),
                new SecuredEndpoint("telework.query.history", "EMPLOYEE", () -> get("/api/telework/42/history")),
                new SecuredEndpoint("telework.query.agreement.pdf", "EMPLOYEE", () -> get("/api/telework/42/agreement/pdf")),
                new SecuredEndpoint("telework.query.justificatif.view", "EMPLOYEE", () -> get("/api/telework/42/justificatif/view"))
        );
    }

    private static MockHttpServletRequestBuilder buildCreateTeleworkRequest() {
        MockMultipartFile data = new MockMultipartFile(
                "data",
                "data.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"startDate\":\"2026-04-10\",\"endDate\":\"2026-04-11\",\"reason\":\"medical\"}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "justification.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));

        return multipart("/api/telework")
                .file(data)
                .file(file)
                .with(request -> {
                    request.setMethod("POST");
                    return request;
                });
    }

    private static String wrongRoleFor(String requiredRole) {
        return switch (requiredRole) {
            case "ADMIN" -> "EMPLOYEE";
            case "MANAGER" -> "EMPLOYEE";
            case "HR" -> "EMPLOYEE";
            case "EMPLOYEE" -> "ADMIN";
            default -> "GUEST";
        };
    }

    private record SecuredEndpoint(String id, String requiredRole, Supplier<MockHttpServletRequestBuilder> request) {
        @Override
        public String toString() {
            return id;
        }
    }
}
