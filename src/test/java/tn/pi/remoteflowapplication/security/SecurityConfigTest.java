package tn.pi.remoteflowapplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "keycloak.admin.server-url=http://localhost:8081",
        "keycloak.admin.realm=test-realm",
        "keycloak.admin.client-id=test-client",
        "keycloak.admin.username=test-user",
        "keycloak.admin.password=test-password"
})
@AutoConfigureMockMvc
@Import(TestSecurityController.class)
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenUnauthenticated_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenAdminRole_thenAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void whenUserRole_thenForbiddenAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenManagerRole_thenAccessManagerEndpoint() throws Exception {
        mockMvc.perform(get("/api/manager/test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isOk());
    }

    @Test
    void whenHRRole_thenAccessHREndpoint() throws Exception {
        mockMvc.perform(get("/api/hr/test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_HR"))))
                .andExpect(status().isOk());
    }

    @Test
    void verifyRolePrefixMapping() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
