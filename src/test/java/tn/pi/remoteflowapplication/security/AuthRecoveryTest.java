package tn.pi.remoteflowapplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRecoveryTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void whenNoToken_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void afterLogout_tokenNotProvided_thenUnauthorized() throws Exception {
        // Simulates frontend logout (token removed)
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized());
    }
}

