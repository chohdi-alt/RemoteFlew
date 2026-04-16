package tn.pi.remoteflowapplication.tests.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import tn.pi.remoteflowapplication.application.dto.EmployeeDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.application.service.DashboardService;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;
import tn.pi.remoteflowapplication.tests.security.support.JwtTestTokenFactory;
import tn.pi.remoteflowapplication.tests.security.support.TestJwtSecurityConfiguration;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import tn.pi.remoteflowapplication.domain.exception.ResourceNotFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@Tag("integration")
@AutoConfigureMockMvc
@Import(TestJwtSecurityConfiguration.class)
class ApiContractIT extends BaseIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private TeleworkStatusQueryService queryService;

    private final JwtTestTokenFactory jwtTestTokenFactory = new JwtTestTokenFactory();

    @Test
    void employeeDashboardShouldAdhereToStrictContract() throws Exception {
        // Stub
        EmployeeDashboardDTO dto = new EmployeeDashboardDTO(
                Map.of(),
                List.of(),
                List.of(new TeleworkStatusDTO(
                        42L,
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        "SUBMITTED",
                        null,
                        false,
                        "medical",
                        "file-123",
                        "m-ok",
                        "h-ok")));

        when(dashboardService.getEmployeeDashboard(anyString(), any(), any()))
                .thenReturn(dto);

        mockMvc.perform(get("/api/dashboard/employee")
                .header("Authorization",
                        jwtTestTokenFactory.bearerTokenForRole("contract-user", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))

                // Structure validation
                .andExpect(jsonPath("$.myTotalsByStatus").isMap())
                .andExpect(jsonPath("$.myMonthlyTrend").isArray())
                .andExpect(jsonPath("$.recentRequests").isArray())
                .andExpect(jsonPath("$.recentRequests[0].requestId").isNumber())
                .andExpect(jsonPath("$.recentRequests[0].status").isString())
                .andExpect(jsonPath("$.recentRequests[0].specialCase").isBoolean());
    }

    @Test
    void errorResponsesShouldHaveConsistentFormat() throws Exception {

        // Simulate NOT FOUND: use ResourceNotFoundException (maps to 404 via GlobalExceptionHandler)
        // Use matchers for ALL arguments — controller passes real Authentication, never null
        when(queryService.findById(anyLong(), any()))
                .thenThrow(new ResourceNotFoundException("Request not found"));

        mockMvc.perform(get("/api/telework/999999")
                .header("Authorization",
                        jwtTestTokenFactory.bearerTokenForRole("contract-user", "MANAGER")))
                .andExpect(status().isNotFound())

                // Contract validation
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").value("/api/telework/999999"));
    }
}