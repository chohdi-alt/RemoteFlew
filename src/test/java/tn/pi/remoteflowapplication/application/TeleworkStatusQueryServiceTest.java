package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeleworkStatusQueryServiceTest {

    @Mock
    private TeleworkRequestRepository teleworkRequestRepository;

    @Test
    void employeeCanReadOwnHistory() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2));

        when(teleworkRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        TeleworkStatusQueryService service = new TeleworkStatusQueryService(teleworkRequestRepository);
        var auth = new UsernamePasswordAuthenticationToken(
                "emp-1",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        var history = service.getRequestHistory(1L, auth);

        assertFalse(history.isEmpty());
    }

    @Test
    void employeeCannotReadAnotherEmployeeHistory() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2));

        when(teleworkRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        TeleworkStatusQueryService service = new TeleworkStatusQueryService(teleworkRequestRepository);
        var auth = new UsernamePasswordAuthenticationToken(
                "emp-2",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        assertThrows(ForbiddenOperationException.class, () -> service.getRequestHistory(1L, auth));
    }

    @Test
    void managerCanReadAnyEmployeeHistory() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2));

        when(teleworkRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        TeleworkStatusQueryService service = new TeleworkStatusQueryService(teleworkRequestRepository);
        var auth = new UsernamePasswordAuthenticationToken(
                "manager-1",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));

        var history = service.getRequestHistory(1L, auth);

        assertFalse(history.isEmpty());
    }
}
