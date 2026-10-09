package com.pahal.billingApp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.enums.Role;
import com.pahal.billingApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void firstLogin_passwordChangeAllowsLoginWithNewPasswordAndClearsFirstLoginFlag() throws Exception {
        User user = saveUser("first-admin", "Auth-Tenant-A");
        String token = login(user.getUserId(), "oldPass@123");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("oldPass@123", "NewSecurePass@123")))
                .andExpect(status().isOk());

        User updated = userRepository.findByUserId(user.getUserId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewSecurePass@123", updated.getPassword())).isTrue();
        assertThat(updated.is_FirstTimeLogin()).isFalse();
        login(user.getUserId(), "NewSecurePass@123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", user.getUserId(), "password", "oldPass@123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_cannotTargetAnotherTenantUserThroughQueryParameter() throws Exception {
        User caller = saveUser("caller-admin", "Auth-Tenant-A");
        User other = saveUser("other-admin", "Auth-Tenant-B");
        String token = login(caller.getUserId(), "oldPass@123");

        mockMvc.perform(post("/api/auth/change-password")
                        .queryParam("userId", other.getUserId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("oldPass@123", "NewSecurePass@123")))
                .andExpect(status().isOk());

        assertThat(passwordEncoder.matches("oldPass@123", other.getPassword())).isTrue();
        assertThat(other.is_FirstTimeLogin()).isTrue();
        assertThat(passwordEncoder.matches("NewSecurePass@123", caller.getPassword())).isTrue();
    }

    @Test
    void changePassword_rejectsLegacyQueryRequestWithoutChangingPassword() throws Exception {
        User user = saveUser("legacy-admin", "Auth-Tenant-A");
        String token = login(user.getUserId(), "oldPass@123");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("userId", user.getUserId())
                        .queryParam("password", "NewSecurePass@123"))
                .andExpect(status().isBadRequest());

        assertThat(passwordEncoder.matches("oldPass@123", user.getPassword())).isTrue();
        assertThat(user.is_FirstTimeLogin()).isTrue();
    }

    @Test
    void changePassword_requiresLogin() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("oldPass@123", "NewSecurePass@123")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void changePassword_wrongCurrentPasswordReturnsValidationErrorAndKeepsLoginValid() throws Exception {
        User user = saveUser("wrong-current-password-admin", "Auth-Tenant-A");
        String token = login(user.getUserId(), "oldPass@123");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("wrong-current-password", "NewSecurePass@123")))
                .andExpect(status().isBadRequest());

        assertThat(passwordEncoder.matches("oldPass@123", user.getPassword())).isTrue();
        assertThat(user.is_FirstTimeLogin()).isTrue();
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("oldPass@123", "NewSecurePass@123")))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_rejectsShortNewPasswordWithoutClearingFirstLoginFlag() throws Exception {
        User user = saveUser("short-password-admin", "Auth-Tenant-A");
        String token = login(user.getUserId(), "oldPass@123");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("oldPass@123", "short")))
                .andExpect(status().isBadRequest());

        assertThat(passwordEncoder.matches("oldPass@123", user.getPassword())).isTrue();
        assertThat(user.is_FirstTimeLogin()).isTrue();
    }

    private User saveUser(String userId, String tenantId) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(Role.ROLE_ADMIN);
        user.setTenantId(tenantId);
        user.setName("Store Admin");
        user.setPassword(passwordEncoder.encode("oldPass@123"));
        user.set_FirstTimeLogin(true);
        return userRepository.save(user);
    }

    private String login(String userId, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", userId, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String passwordChangeBody(String currentPassword, String newPassword) throws Exception {
        return objectMapper.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword));
    }
}
