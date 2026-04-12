package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeleworkStatusQueryServiceTest {

    @Mock
    private TeleworkRequestRepository teleworkRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private DocumentStoragePort documentStoragePort;

    private TeleworkStatusQueryService service;

    @BeforeEach
    void setUp() {
        service = new TeleworkStatusQueryService(
                teleworkRequestRepository,
                userRepository,
                teamRepository,
                documentStoragePort);
    }

    @Test
    void managerCanAccessHistoryForOwnTeam() {
        TeleworkRequest request = requestFor("employee-1", 10L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(1L)).thenReturn(Optional.of(request));
        when(userRepository.findTeamIdByExternalId("employee-1")).thenReturn(Optional.of(10L));
        when(teamRepository.findManagedTeamIdsByUsername("manager-1")).thenReturn(List.of(10L));

        Authentication manager = auth("manager-1", "ROLE_MANAGER");

        assertThatCode(() -> service.getRequestHistory(1L, manager))
                .doesNotThrowAnyException();
    }

    @Test
    void managerCannotAccessHistoryForOtherTeam() {
        TeleworkRequest request = requestFor("employee-2", 20L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(2L)).thenReturn(Optional.of(request));
        when(teamRepository.findManagedTeamIdsByUsername("manager-1")).thenReturn(List.of(10L));
        when(userRepository.findTeamIdByExternalId("manager-1")).thenReturn(Optional.of(10L));
        when(userRepository.findTeamIdByExternalId("employee-2")).thenReturn(Optional.of(20L));

        Authentication manager = auth("manager-1", "ROLE_MANAGER");

        assertThatThrownBy(() -> service.getRequestHistory(2L, manager))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Manager cannot access this user's history");
    }

    @Test
    void hrCanAccessAnyHistory() {
        TeleworkRequest request = requestFor("employee-3", 77L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(3L)).thenReturn(Optional.of(request));

        Authentication hr = auth("hr-1", "ROLE_HR");

        assertThatCode(() -> service.getRequestHistory(3L, hr))
                .doesNotThrowAnyException();
    }

    @Test
    void adminCanAccessAnyHistory() {
        TeleworkRequest request = requestFor("employee-4", 88L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(4L)).thenReturn(Optional.of(request));

        Authentication admin = auth("admin-1", "ROLE_ADMIN");

        assertThatCode(() -> service.getRequestHistory(4L, admin))
                .doesNotThrowAnyException();
    }

    @Test
    void userCanAccessOwnHistory() {
        TeleworkRequest request = requestFor("user-1", 11L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(5L)).thenReturn(Optional.of(request));

        Authentication user = auth("user-1", "ROLE_USER");

        assertThatCode(() -> service.getRequestHistory(5L, user))
                .doesNotThrowAnyException();
    }

    @Test
    void userCannotAccessOtherUserHistory() {
        TeleworkRequest request = requestFor("user-2", 11L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(6L)).thenReturn(Optional.of(request));

        Authentication user = auth("user-1", "ROLE_USER");

        assertThatThrownBy(() -> service.getRequestHistory(6L, user))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("User cannot access others");
    }

    @Test
    void employeeCanAccessOwnHistory() {
        TeleworkRequest request = requestFor("employee-9", 14L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(9L)).thenReturn(Optional.of(request));

        Authentication employee = auth("employee-9", "ROLE_EMPLOYEE");

        assertThatCode(() -> service.getRequestHistory(9L, employee))
                .doesNotThrowAnyException();
    }

    @Test
    void employeeCannotAccessOtherEmployeeHistory() {
        TeleworkRequest request = requestFor("employee-10", 15L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(10L)).thenReturn(Optional.of(request));

        Authentication employee = auth("employee-9", "ROLE_EMPLOYEE");

        assertThatThrownBy(() -> service.getRequestHistory(10L, employee))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("User cannot access others");
    }

    @Test
    void managerScopeFallsBackToSameTeamWhenManagedTeamsAreMissing() {
        TeleworkRequest request = requestFor("employee-7", 30L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(7L)).thenReturn(Optional.of(request));
        when(userRepository.findTeamIdByExternalId("employee-7")).thenReturn(Optional.of(30L));
        when(teamRepository.findManagedTeamIdsByUsername("manager-7")).thenReturn(List.of());
        when(userRepository.findTeamIdByExternalId("manager-7")).thenReturn(Optional.of(30L));

        Authentication manager = auth("manager-7", "ROLE_MANAGER");

        assertThatCode(() -> service.getRequestHistory(7L, manager))
                .doesNotThrowAnyException();
    }

    @Test
    void managerScopeSkipsFallbackLookupWhenManagedTeamAlreadyMatches() {
        TeleworkRequest request = requestFor("employee-8", 31L);
        when(teleworkRequestRepository.findByIdWithAuditLogs(8L)).thenReturn(Optional.of(request));
        when(userRepository.findTeamIdByExternalId("employee-8")).thenReturn(Optional.of(31L));
        when(teamRepository.findManagedTeamIdsByUsername("manager-8")).thenReturn(List.of(31L));

        Authentication manager = auth("manager-8", "ROLE_MANAGER");

        assertThatCode(() -> service.getRequestHistory(8L, manager))
                .doesNotThrowAnyException();
        verify(userRepository, never()).findTeamIdByExternalId("manager-8");
    }

    private static TeleworkRequest requestFor(String employeeId, Long teamId) {
        TeleworkRequest request = TeleworkRequest.create(
                employeeId,
                LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(3));
        request.assignTeamId(teamId);
        return request;
    }

    private static Authentication auth(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "n/a",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
