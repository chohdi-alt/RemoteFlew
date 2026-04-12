package tn.pi.remoteflowapplication.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tn.pi.remoteflowapplication.application.dto.AgreementFileDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.impl.AgreementServiceImpl;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementServiceImplTest {

    @Mock
    private TeleworkRequestRepository teleworkRequestRepository;

    @Mock
    private DocumentStoragePort documentStoragePort;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    private AgreementServiceImpl agreementService;

    @BeforeEach
    void setUp() {
        agreementService = new AgreementServiceImpl(
                teleworkRequestRepository,
                documentStoragePort,
                userRepository,
                teamRepository);
    }

    @Test
    void managerCanDownloadAgreementForOwnTeam() {
        TeleworkRequest request = approvedRequest("employee-1", 10L, "agreement-node-1");
        when(teleworkRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findTeamIdByExternalId("employee-1")).thenReturn(Optional.of(10L));
        when(teamRepository.findManagedTeamIdsByUsername("manager-1")).thenReturn(List.of(10L));
        when(documentStoragePort.download("agreement-node-1")).thenReturn(new byte[] {1, 2, 3});

        AgreementFileDTO file = agreementService.downloadAgreementPdf(1L, auth("manager-1", "ROLE_MANAGER"));

        assertThat(file).isNotNull();
        assertThat(file.content()).hasSize(3);
    }

    @Test
    void managerCannotDownloadAgreementForOtherTeam() {
        TeleworkRequest request = approvedRequest("employee-2", 20L, "agreement-node-2");
        when(teleworkRequestRepository.findById(2L)).thenReturn(Optional.of(request));
        when(userRepository.findTeamIdByExternalId("employee-2")).thenReturn(Optional.of(20L));
        when(teamRepository.findManagedTeamIdsByUsername("manager-1")).thenReturn(List.of(10L));
        when(userRepository.findTeamIdByExternalId("manager-1")).thenReturn(Optional.of(10L));

        assertThatThrownBy(() -> agreementService.downloadAgreementPdf(2L, auth("manager-1", "ROLE_MANAGER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Manager cannot access");
    }

    @Test
    void hrAndAdminRemainUnrestricted() {
        TeleworkRequest request = approvedRequest("employee-3", 30L, "agreement-node-3");
        when(teleworkRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(documentStoragePort.download("agreement-node-3")).thenReturn(new byte[] {9});

        assertThatCode(() -> agreementService.downloadAgreementPdf(3L, auth("hr-1", "ROLE_HR")))
                .doesNotThrowAnyException();
        assertThatCode(() -> agreementService.downloadAgreementPdf(3L, auth("admin-1", "ROLE_ADMIN")))
                .doesNotThrowAnyException();
    }

    private static TeleworkRequest approvedRequest(String employeeId, Long teamId, String agreementNodeId) {
        TeleworkRequest request = TeleworkRequest.create(
                employeeId,
                LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(1));
        request.assignTeamId(teamId);
        request.approveByManager("manager ok");
        request.approveByHR("hr ok");
        request.linkAgreementNode(agreementNodeId);
        return request;
    }

    private static Authentication auth(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "n/a",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
