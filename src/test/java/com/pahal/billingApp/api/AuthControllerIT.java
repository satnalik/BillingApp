package com.pahal.billingApp.api;

import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.enums.Role;
import com.pahal.billingApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void changePassword_updatesExistingUserByUserIdParam() throws Exception {
        User user = new User();
        user.setUserId("cashier1");
        user.setRole(Role.ROLE_CASHIER);
        user.setTenantId("Tenant-A");
        user.setName("Cashier 1");
        user.setPassword(passwordEncoder.encode("oldPass@123"));
        user.set_FirstTimeLogin(true);
        userRepository.save(user);

        mockMvc.perform(
                        post("/api/auth/change-password")
                                .queryParam("userId", "cashier1")
                                .queryParam("password", "newPass@123")
                )
                .andExpect(status().isOk());

        User updated = userRepository.findByUserId("cashier1").orElseThrow();
        assertThat(passwordEncoder.matches("newPass@123", updated.getPassword())).isTrue();
        assertThat(updated.is_FirstTimeLogin()).isFalse();
    }
}
