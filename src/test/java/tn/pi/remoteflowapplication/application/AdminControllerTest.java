package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.application.service.AdminUserService;
import tn.pi.remoteflowapplication.application.service.AuditLogQueryService;
import tn.pi.remoteflowapplication.controller.AdminController;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private AuditLogQueryService auditLogQueryService;

    @InjectMocks
    private AdminController adminController;

    @Test
    void syncUsersDelegatesToAdminService() {
        adminController.syncUsersFromKeycloak();
        verify(adminUserService, times(1)).syncUsersFromKeycloak();
    }
}
