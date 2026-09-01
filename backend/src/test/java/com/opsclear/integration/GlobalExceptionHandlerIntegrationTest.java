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
import com.opsclear.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("GlobalExceptionHandler — generic error handler wired into Spring context")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProjectService projectService;
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
    }

    @Test
    @DisplayName("handleGeneric_shouldReturn500WithGenericMessage_whenUnexpectedExceptionThrown")
    void handleGeneric_shouldReturn500WithGenericMessage_whenUnexpectedExceptionThrown() throws Exception {
        when(projectService.getProjectsForMember(any(), any())).thenThrow(new RuntimeException("unexpected db failure"));

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "u@example.com"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("handleNoResourceFound_shouldReturn404_forAnUnmappedRoute (JOB-253)")
    void handleNoResourceFound_shouldReturn404_forAnUnmappedRoute() throws Exception {
        mockMvc.perform(delete("/api/projects/x/jobs/y/notes/z")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "u@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
