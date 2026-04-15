package tn.pi.remoteflowapplication.tests.workflow;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.tests.integration.BaseIntegrationIT;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("workflow")
@Transactional
class WorkflowIntegrityIT extends BaseIntegrationIT {

    @Autowired
    private CreateTeleworkRequestHandler handler;

    @Autowired
    private TeleworkRequestRepository repository;

    @Test
    void shouldMaintainDataIntegrityUponRejection() throws Exception {
        User employee = new User("integrity-emp", "emp", "Employee", "emp@test.local");
        
        // 1. Create a request for Monday
        CreateTeleworkDTO dto1 = new CreateTeleworkDTO(
                LocalDate.of(2026, 6, 1), 
                LocalDate.of(2026, 6, 1), 
                "Quota test 1");
        Long requestId = handler.handle(dto1, null, employee);
        
        TeleworkRequest request = repository.findById(requestId).orElseThrow();
        assertEquals(RequestStatus.SUBMITTED, request.getStatus());

        // 2. Try to create another request for Tuesday in the same week (Quota is 1)
        CreateTeleworkDTO dto2 = new CreateTeleworkDTO(
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 2),
                "Quota test 2");
        assertThrows(BusinessException.class, () -> handler.handle(dto2, null, employee), 
                "Should block second request due to quota");

        // 3. Reject the first request
        request.rejectByManager("Quota release test");
        repository.save(request);

        // 4. Now the second request should pass because the first one is REJECTED
        Long newRequestId = handler.handle(dto2, null, employee);
        assertNotNull(newRequestId);
        assertEquals(RequestStatus.SUBMITTED, repository.findById(newRequestId).get().getStatus());
    }

    @Test
    void shouldBlockOverlappingRequestsForSameEmployee() throws Exception {
        User employee = new User("overlap-emp", "emp2", "Employee", "emp2@test.local");

        CreateTeleworkDTO dto1 = new CreateTeleworkDTO(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2),
                "Overlap 1");
        handler.handle(dto1, null, employee);

        CreateTeleworkDTO dtoOverlap = new CreateTeleworkDTO(
                LocalDate.of(2026, 7, 2), 
                LocalDate.of(2026, 7, 3),
                "Overlap 2");
        
        assertThrows(BusinessException.class, () -> handler.handle(dtoOverlap, null, employee));
    }
}
