package tn.pi.remoteflowapplication.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.dto.PendingValidationTaskDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.service.ValidationInboxService;
import tn.pi.remoteflowapplication.application.service.WorkflowTaskService;
import tn.pi.remoteflowapplication.domain.entity.TaskEntity;
import tn.pi.remoteflowapplication.domain.entity.Team;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTaskJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeamJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringUserJpaRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zeebe_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "camunda.client.zeebe.enabled=true",
        "zeebe.client.enabled=true",
        "zeebe.client.security.plaintext=true"
})
class ZeebeEndToEndIT {

    @Autowired
    private CreateTeleworkRequestHandler createHandler;
    @Autowired
    private ManagerApprovalHandler managerApprovalHandler;
    @Autowired
    private HrApprovalHandler hrApprovalHandler;
    @Autowired
    private WorkflowTaskService workflowTaskService;
    @Autowired
    private ValidationInboxService validationInboxService;
    @Autowired
    private TeleworkRequestRepository teleworkRequestRepository;
    @Autowired
    private SpringUserJpaRepository userRepository;
    @Autowired
    private SpringTeamJpaRepository teamRepository;
    @Autowired
    private SpringTaskJpaRepository taskRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DocumentStoragePort documentStoragePort;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        seedUsersAndTeam();
        resetRequestIdSequence();
        when(documentStoragePort.upload(any(MultipartFile.class), anyString()))
                .thenReturn("node-1");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void endToEnd_specialCase_reachesHrApproval() throws Exception {
        authenticateAs("employee-1", "ROLE_EMPLOYEE");

        CreateTeleworkDTO dto = new CreateTeleworkDTO(
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 4),
                "Need 2 days");

        MultipartFile justificatif = new MockMultipartFile(
                "file",
                "justificatif.txt",
                "text/plain",
                "ok".getBytes());

        Long requestId = createHandler.handle(dto, justificatif);

        TaskEntity managerTask = waitForPendingTask(requestId, "MANAGER", Duration.ofSeconds(40));
        assertEquals(requestId, managerTask.getRequestId());

        // Simulate worker retry after restart/crash with same Zeebe job key.
        TaskEntity duplicateAttempt = workflowTaskService.createTask(
                requestId,
                "MANAGER",
                managerTask.getJobKey());
        assertEquals(managerTask.getId(), duplicateAttempt.getId());
        assertEquals(1L, countTasksByJobKey(managerTask.getJobKey()));

        Page<PendingValidationTaskDTO> managerInbox = validationInboxService.getPendingValidations(
                auth("manager-1", "ROLE_MANAGER"),
                PageRequest.of(0, 20));
        assertTrue(managerInbox.getContent().stream().anyMatch(t -> requestId.equals(t.requestId())));

        authenticateAs("manager-1", "ROLE_MANAGER");
        managerApprovalHandler.approve(
                requestId,
                String.valueOf(managerTask.getId()),
                new ApprovalDecisionDTO(requestId, "manager-1", "ok"));

        TaskEntity hrTask = waitForPendingTask(requestId, "HR", Duration.ofSeconds(40));
        assertEquals(requestId, hrTask.getRequestId());

        Page<PendingValidationTaskDTO> hrInbox = validationInboxService.getPendingValidations(
                auth("hr-1", "ROLE_HR"),
                PageRequest.of(0, 20));
        assertTrue(hrInbox.getContent().stream().anyMatch(t -> requestId.equals(t.requestId())));

        authenticateAs("hr-1", "ROLE_HR");
        hrApprovalHandler.approve(
                requestId,
                String.valueOf(hrTask.getId()),
                new ApprovalDecisionDTO(requestId, "hr-1", "ok"));

        waitForTaskStatus(managerTask.getId(), "COMPLETED", Duration.ofSeconds(20));
        waitForTaskStatus(hrTask.getId(), "COMPLETED", Duration.ofSeconds(20));

        TeleworkRequest request = teleworkRequestRepository.findById(requestId).orElseThrow();
        assertEquals(RequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void endToEnd_normalCase_managerApprovalStillRoutesToHr() throws Exception {
        authenticateAs("employee-1", "ROLE_EMPLOYEE");

        CreateTeleworkDTO dto = new CreateTeleworkDTO(
                LocalDate.of(2026, 3, 6),
                LocalDate.of(2026, 3, 6),
                "Need 1 day");

        MultipartFile justificatif = new MockMultipartFile(
                "file",
                "justificatif.txt",
                "text/plain",
                "ok".getBytes());

        Long requestId = createHandler.handle(dto, justificatif);

        TaskEntity managerTask = waitForPendingTask(requestId, "MANAGER", Duration.ofSeconds(40));
        authenticateAs("manager-1", "ROLE_MANAGER");
        managerApprovalHandler.approve(
                requestId,
                String.valueOf(managerTask.getId()),
                new ApprovalDecisionDTO(requestId, "manager-1", "ok"));

        TaskEntity hrTask = waitForPendingTask(requestId, "HR", Duration.ofSeconds(40));
        assertEquals(requestId, hrTask.getRequestId());

        authenticateAs("hr-1", "ROLE_HR");
        hrApprovalHandler.reject(
                requestId,
                String.valueOf(hrTask.getId()),
                new ApprovalDecisionDTO(requestId, "hr-1", "not approved"));

        waitForTaskStatus(managerTask.getId(), "COMPLETED", Duration.ofSeconds(20));
        waitForTaskStatus(hrTask.getId(), "COMPLETED", Duration.ofSeconds(20));

        TeleworkRequest request = teleworkRequestRepository.findById(requestId).orElseThrow();
        assertEquals(RequestStatus.REJECTED, request.getStatus());
    }

    private TaskEntity waitForPendingTask(Long requestId, String type, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Optional<TaskEntity> task = Optional.empty();
        while (System.currentTimeMillis() < deadline) {
            task = workflowTaskService.getPendingTaskForRequest(requestId, type);
            if (task.isPresent()) {
                return task.get();
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for " + type + " task for request " + requestId);
    }

    private void waitForTaskStatus(Long taskId, String expectedStatus, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<TaskEntity> task = taskRepository.findById(taskId);
            if (task.isPresent() && expectedStatus.equals(task.get().getStatus())) {
                return;
            }
            Thread.sleep(300);
        }
        Optional<TaskEntity> task = taskRepository.findById(taskId);
        String actual = task.map(TaskEntity::getStatus).orElse("MISSING");
        throw new AssertionError("Timed out waiting for task " + taskId + " status " + expectedStatus
                + " (actual=" + actual + ")");
    }

    private void seedUsersAndTeam() {
        User manager = userRepository.findByUsername("manager-1")
                .orElseGet(() -> userRepository.save(new User("manager-1", "Manager One", "manager-1@example.com")));

        User hr = userRepository.findByUsername("hr-1")
                .orElseGet(() -> userRepository.save(new User("hr-1", "HR One", "hr-1@example.com")));

        Team team = teamRepository.findByNom("Team A").orElseGet(() -> {
            Team t = new Team("Team A");
            t.setManager(manager);
            return teamRepository.save(t);
        });
        if (team.getManager() == null) {
            team.setManager(manager);
            teamRepository.save(team);
        }

        User employee = userRepository.findByUsername("employee-1")
                .orElseGet(() -> new User("employee-1", "Employee One", "employee-1@example.com"));
        employee.assignTeam(team);
        userRepository.save(employee);

        if (manager.getEquipe() == null) {
            manager.assignTeam(team);
            userRepository.save(manager);
        }
        if (hr.getEquipe() == null) {
            userRepository.save(hr);
        }
    }

    private void resetRequestIdSequence() {
        long base = 1_000_000L + (System.currentTimeMillis() % 1_000_000L);
        jdbcTemplate.execute("ALTER TABLE telework_requests ALTER COLUMN id RESTART WITH " + base);
    }

    private long countTasksByJobKey(Long jobKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zeebe_tasks WHERE job_key = ?",
                Long.class,
                jobKey);
    }

    private void authenticateAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private UsernamePasswordAuthenticationToken auth(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "n/a",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
