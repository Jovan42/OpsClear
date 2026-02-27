package com.opsclear.integration;

import com.opsclear.model.UserModel;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.USERS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Health endpoint should be accessible without authentication")
    void healthEndpoint_isAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Protected endpoint should return 401 without authentication")
    void protectedEndpoint_returns401_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint should return 200 with valid JWT")
    void protectedEndpoint_returns200_withValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String name = "Test User";

        mockMvc.perform(get("/api/health")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(userId.toString())
                                        .claim("email", email)
                                        .claim("name", name))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("User should be created in database after first authenticated request")
    void userShouldBeCreated_afterFirstAuthenticatedRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "newuser@example.com";
        String name = "New User";

        assertThat(userRepository.findById(userId)).isEmpty();

        mockMvc.perform(get("/api/health")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(userId.toString())
                                        .claim("email", email)
                                        .claim("name", name))))
                .andExpect(status().isOk());

        Optional<UserModel> savedUser = userRepository.findById(userId);
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo(email);
        assertThat(savedUser.get().getName()).isEqualTo(name);
    }

    @Test
    @DisplayName("User should be updated on subsequent authenticated requests")
    void userShouldBeUpdated_onSubsequentAuthenticatedRequests() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        String originalName = "Original Name";
        String updatedName = "Updated Name";

        mockMvc.perform(get("/api/health")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(userId.toString())
                                        .claim("email", email)
                                        .claim("name", originalName))))
                .andExpect(status().isOk());

        UserModel originalUser = userRepository.findById(userId).orElseThrow();
        assertThat(originalUser.getName()).isEqualTo(originalName);

        mockMvc.perform(get("/api/health")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(userId.toString())
                                        .claim("email", email)
                                        .claim("name", updatedName))))
                .andExpect(status().isOk());

        UserModel updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(updatedUser.getName()).isEqualTo(updatedName);
        assertThat(updatedUser.getLastLoginAt()).isAfter(originalUser.getCreatedAt());
    }

    @Test
    @DisplayName("User sync should handle name from given_name and family_name")
    void userSync_handlesGivenAndFamilyName() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        mockMvc.perform(get("/api/health")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(userId.toString())
                                        .claim("email", email)
                                        .claim("given_name", "John")
                                        .claim("family_name", "Doe"))))
                .andExpect(status().isOk());

        UserModel user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("User sync should fall through a blank name claim to given_name and family_name")
    void userSync_handlesBlankNameClaim() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        mockMvc.perform(get("/api/health")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(userId.toString())
                                        .claim("email", email)
                                        .claim("name", "   ")
                                        .claim("given_name", "Jane")
                                        .claim("family_name", "Smith"))))
                .andExpect(status().isOk());

        UserModel user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getName()).isEqualTo("Jane Smith");
    }

    @Test
    @DisplayName("User sync should fallback to preferred_username when name claims are absent")
    void userSync_handlesPreferredUsername() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        mockMvc.perform(get("/api/health")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(userId.toString())
                                        .claim("email", email)
                                        .claim("preferred_username", "jsmith"))))
                .andExpect(status().isOk());

        UserModel user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getName()).isEqualTo("jsmith");
    }

    @Test
    @DisplayName("findById should return null lastLoginAt when last_login_at is null in DB")
    void findById_shouldHandleNullLastLoginAt() {
        UUID userId = UUID.randomUUID();
        dsl.insertInto(USERS)
                .set(USERS.ID, userId)
                .set(USERS.EMAIL, "nologin@example.com")
                .set(USERS.NAME, "No Login User")
                .set(USERS.CREATED_AT, LocalDateTime.now())
                .execute();

        Optional<UserModel> user = userRepository.findById(userId);
        assertThat(user).isPresent();
        assertThat(user.get().getLastLoginAt()).isNull();
    }
}
