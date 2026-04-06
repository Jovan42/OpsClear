package com.opsclear.integration;

import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.model.UserModel;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("User search endpoint")
class UserControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID callerId;

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
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        callerId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(callerId).email("caller@example.com").name("Caller").build());
    }

    @Test
    @DisplayName("search_shouldReturnMatchingUsers_whenEmailPrefixMatches")
    void search_shouldReturnMatchingUsers_whenEmailPrefixMatches() throws Exception {
        userRepository.save(UserModel.builder()
                .id(UUID.randomUUID()).email("alice@example.com").name("Alice").build());
        userRepository.save(UserModel.builder()
                .id(UUID.randomUUID()).email("alicia@example.com").name("Alicia").build());
        userRepository.save(UserModel.builder()
                .id(UUID.randomUUID()).email("bob@example.com").name("Bob").build());

        mockMvc.perform(get(ApiPaths.usersSearch("ali"))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[1].email").value("alicia@example.com"));
    }

    @Test
    @DisplayName("search_shouldBeCaseInsensitive")
    void search_shouldBeCaseInsensitive() throws Exception {
        userRepository.save(UserModel.builder()
                .id(UUID.randomUUID()).email("Alice@Example.COM").name("Alice").build());

        mockMvc.perform(get(ApiPaths.usersSearch("alice"))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice"));
    }

    @Test
    @DisplayName("search_shouldReturnEmpty_whenNoMatch")
    void search_shouldReturnEmpty_whenNoMatch() throws Exception {
        mockMvc.perform(get(ApiPaths.usersSearch("zzz"))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("search_shouldReturn400_whenEmailPrefixTooShort")
    void search_shouldReturn400_whenEmailPrefixTooShort() throws Exception {
        mockMvc.perform(get(ApiPaths.usersSearch("a"))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search_shouldReturn401_withoutAuthentication")
    void search_shouldReturn401_withoutAuthentication() throws Exception {
        mockMvc.perform(get(ApiPaths.usersSearch("ali")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("search_shouldLimitResultsToTen")
    void search_shouldLimitResultsToTen() throws Exception {
        for (int i = 0; i < 15; i++) {
            userRepository.save(UserModel.builder()
                    .id(UUID.randomUUID())
                    .email("user" + i + "@example.com")
                    .name("User " + i)
                    .build());
        }

        mockMvc.perform(get(ApiPaths.usersSearch("user"))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    @DisplayName("search_shouldReturnIdNameEmail_inResponse")
    void search_shouldReturnIdNameEmail_inResponse() throws Exception {
        UUID targetId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(targetId).email("jane@example.com").name("Jane Doe").build());

        mockMvc.perform(get(ApiPaths.usersSearch("jane"))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(targetId.toString()))
                .andExpect(jsonPath("$[0].name").value("Jane Doe"))
                .andExpect(jsonPath("$[0].email").value("jane@example.com"));
    }

    @Test
    @DisplayName("search_shouldReturn400WithMultipleMessages_whenEmailIsBlankAndTooShort")
    void search_shouldReturn400WithMultipleMessages_whenEmailIsBlankAndTooShort() throws Exception {
        // Single space: violates both @NotBlank and @Size(min=2) simultaneously,
        // exercising the reduce((a, b) -> a + "; " + b) branch in handleConstraintViolation.
        mockMvc.perform(get(ApiPaths.USERS).param("email", " ")
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(";")));
    }
}
