package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code opsclear.signup.open=false} closes organisation creation until launch (ADR-0066, JOB-441). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "opsclear.signup.open=false")
@DisplayName("Sign-up switch, closed")
class SignupClosedIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgInviteRepository orgInviteRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private UUID superUserId;

    // Accepting an invite creates a notification (JOB-279): leave none behind, or they block
    // other classes from deleting organisations, projects and users.
    @AfterEach
    void deleteNotifications() {
        notificationRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        // TRUNCATE ... CASCADE (UserRepository.deleteAll) clears organisations, invites and whatever else is left
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        superUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(userId).email("user@example.com").name("User").build());
        userRepository.save(UserModel.builder().id(superUserId).email("super@example.com").name("Super").build());
        dsl.update(USERS).set(USERS.SUPER_USER, true).where(USERS.ID.eq(superUserId)).execute();
    }

    @Test
    @DisplayName("status_shouldReturnClosed")
    void status_shouldReturnClosed() throws Exception {
        mockMvc.perform(get(ApiPaths.PUBLIC_SIGNUP_STATUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
    }

    @Test
    @DisplayName("createOrganisation_shouldReturn403AndCreateNothing_forNormalUser")
    void createOrganisation_shouldReturn403AndCreateNothing_forNormalUser() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Sign-up is closed until launch"));

        assertThat(organisationRepository.findByMember(userId)).isEmpty();
        assertThat(organisationRepository.existsBySlugAndDeletedAtIsNull("ACM")).isFalse();
    }

    @Test
    @DisplayName("createOrganisation_shouldReturn201_forSuperUser")
    void createOrganisation_shouldReturn201_forSuperUser() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Test Org", "slug", "TST"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("TST"));

        assertThat(organisationRepository.findByMember(superUserId)).isPresent();
    }

    @Test
    @DisplayName("acceptInvite_shouldStillJoinTheOrg_whenSignupIsClosed")
    void acceptInvite_shouldStillJoinTheOrg_whenSignupIsClosed() throws Exception {
        UUID orgId = organisationRepository.save(OrganisationModel.builder()
                .name("Acme Corp").slug("ACM").createdBy(superUserId).build()).getId();
        organisationRepository.saveMember(orgId, superUserId, OrganisationRole.OWNER);
        orgInviteRepository.save(OrgInviteModel.builder()
                .organisationId(orgId)
                .email("user@example.com")
                .invitedBy(superUserId)
                .token("closed-signup-token")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(post(ApiPaths.acceptInvite("closed-signup-token"))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isOk());

        assertThat(organisationRepository.findMemberRole(orgId, userId)).contains(OrganisationRole.MEMBER);
    }
}
