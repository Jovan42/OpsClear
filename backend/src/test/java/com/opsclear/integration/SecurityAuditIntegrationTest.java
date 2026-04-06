package com.opsclear.integration;

import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security audit")
class SecurityAuditIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Security headers ---

    @Test
    @DisplayName("Authenticated response should include X-Content-Type-Options: nosniff")
    void response_shouldIncludeContentTypeOptionsHeader() throws Exception {
        mockMvc.perform(get(ApiPaths.HEALTH)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "u@example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Authenticated response should include X-Frame-Options: DENY")
    void response_shouldIncludeFrameOptionsHeader() throws Exception {
        mockMvc.perform(get(ApiPaths.HEALTH)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "u@example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("Authenticated response should include Referrer-Policy: no-referrer")
    void response_shouldIncludeReferrerPolicyHeader() throws Exception {
        mockMvc.perform(get(ApiPaths.HEALTH)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "u@example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("Authenticated response should include Permissions-Policy restricting camera, microphone, geolocation")
    void response_shouldIncludePermissionsPolicyHeader() throws Exception {
        mockMvc.perform(get(ApiPaths.HEALTH)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "u@example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    // --- Unauthenticated access ---

    @Test
    @DisplayName("Protected endpoint should return 401 without a token")
    void protectedEndpoint_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get(ApiPaths.PROJECTS))
                .andExpect(status().isUnauthorized());
    }

    // --- Generic error handler ---

    @Test
    @DisplayName("Error response should contain error, message, and timestamp fields")
    void errorResponse_shouldContainStandardFields() throws Exception {
        // Trigger a 404 NotFoundException via a well-formed request for a non-existent resource
        UUID callerId = UUID.randomUUID();
        UUID unknownProject = UUID.randomUUID();

        mockMvc.perform(get(ApiPaths.project(unknownProject))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "u@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Error response should not expose stack trace")
    void errorResponse_shouldNotExposeStackTrace() throws Exception {
        UUID callerId = UUID.randomUUID();
        UUID unknownProject = UUID.randomUUID();

        mockMvc.perform(get(ApiPaths.project(unknownProject))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "u@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

}
