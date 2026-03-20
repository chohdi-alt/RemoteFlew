package tn.pi.remoteflowapplication.telework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringUserJpaRepository;
import tn.pi.remoteflowapplication.domain.entity.User;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class TeleworkWorkflowIntegrationTest {

    @Autowired
    private CreateTeleworkRequestHandler createHandler;

    @Autowired
    private TeleworkRequestRepository repository;

    @Autowired
    private SpringUserJpaRepository userRepository;

    @MockBean
    private CamundaWorkflowService camundaWorkflowService;

    @MockBean
    private AlfrescoDocumentService documentService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        seedEmployeeUser();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        repository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void submitTeleworkRequest_Successful() throws Exception {
        authenticateAs("employee-1");

        CreateTeleworkDTO dto = new CreateTeleworkDTO(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 3),
                "Need 2 days");

        MultipartFile justificatif = new MockMultipartFile(
                "file",
                "justificatif.txt",
                "text/plain",
                "ok".getBytes());

        when(documentService.upload(any(MultipartFile.class), anyString()))
                .thenReturn("node-1");
        when(camundaWorkflowService.startTeleworkProcess(any(), eq("employee-1"), anyBoolean()))
                .thenReturn("proc-1");

        Long requestId = createHandler.handle(dto, justificatif);
        TeleworkRequest request = repository.findById(requestId).orElseThrow();

        assertNotNull(request);
    }

    private void authenticateAs(String username) {
        String role = "ROLE_EMPLOYEE";
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void seedEmployeeUser() {
        String username = "employee-1";
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(new User(username, "Employee One", "employee-1@example.com"));
        }
    }
}
