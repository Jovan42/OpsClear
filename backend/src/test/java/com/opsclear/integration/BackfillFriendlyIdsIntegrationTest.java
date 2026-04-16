package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.MilestoneModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrganisationRepository;
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

import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SEQUENCES;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.MILESTONES;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("V014 backfill friendly IDs migration")
class BackfillFriendlyIdsIntegrationTest {

    @Autowired private DSLContext dsl;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private UUID orgId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(userId).email("owner@example.com").name("Owner").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Acme").slug("ACM").createdBy(userId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, userId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Main Project").ownerId(userId).organisationId(orgId)
                        .status(ProjectStatus.ACTIVE).build());
        projectId = project.getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(ProjectMemberRole.OWNER).build());
    }

    // --- helpers ---

    /** Clears friendly_id on all existing records so the backfill SQL has rows to process. */
    private void clearFriendlyIds() {
        dsl.update(PROJECTS).setNull(PROJECTS.FRIENDLY_ID).execute();
        dsl.update(JOBS).setNull(JOBS.FRIENDLY_ID).execute();
        dsl.update(MILESTONES).setNull(MILESTONES.FRIENDLY_ID).execute();
        dsl.update(ORG_SEQUENCES).set(ORG_SEQUENCES.LAST_VALUE, 0).execute();
    }

    /** Runs the same SQL blocks that V014 contains. */
    private void runBackfill() {
        dsl.execute("""
                WITH ranked AS (
                    SELECT p.id,
                           s.project_prefix,
                           ROW_NUMBER() OVER (PARTITION BY p.organisation_id ORDER BY p.created_at) AS rn
                    FROM   projects p
                    JOIN   org_settings s ON s.org_id = p.organisation_id
                    WHERE  p.friendly_id IS NULL
                )
                UPDATE projects p
                SET    friendly_id = r.project_prefix || '-' || LPAD(r.rn::text, 3, '0')
                FROM   ranked r
                WHERE  p.id = r.id
                """);

        dsl.execute("""
                WITH ranked AS (
                    SELECT j.id,
                           s.job_prefix,
                           ROW_NUMBER() OVER (PARTITION BY proj.organisation_id ORDER BY j.created_at) AS rn
                    FROM   jobs j
                    JOIN   projects proj ON proj.id = j.project_id
                    JOIN   org_settings s ON s.org_id = proj.organisation_id
                    WHERE  j.friendly_id IS NULL
                )
                UPDATE jobs j
                SET    friendly_id = r.job_prefix || '-' || LPAD(r.rn::text, 3, '0')
                FROM   ranked r
                WHERE  j.id = r.id
                """);

        dsl.execute("""
                WITH ranked AS (
                    SELECT m.id,
                           s.milestone_prefix,
                           ROW_NUMBER() OVER (PARTITION BY proj.organisation_id ORDER BY m.created_at) AS rn
                    FROM   milestones m
                    JOIN   projects proj ON proj.id = m.project_id
                    JOIN   org_settings s ON s.org_id = proj.organisation_id
                    WHERE  m.friendly_id IS NULL
                )
                UPDATE milestones m
                SET    friendly_id = r.milestone_prefix || '-' || LPAD(r.rn::text, 3, '0')
                FROM   ranked r
                WHERE  m.id = r.id
                """);

        dsl.execute("""
                UPDATE org_sequences os
                SET    last_value = COALESCE((
                    SELECT COUNT(*) FROM projects p WHERE p.organisation_id = os.org_id
                ), 0)
                WHERE  os.entity_type = 'PROJECT'
                """);

        dsl.execute("""
                UPDATE org_sequences os
                SET    last_value = COALESCE((
                    SELECT COUNT(*) FROM jobs j
                    JOIN projects p ON p.id = j.project_id
                    WHERE p.organisation_id = os.org_id
                ), 0)
                WHERE  os.entity_type = 'JOB'
                """);

        dsl.execute("""
                UPDATE org_sequences os
                SET    last_value = COALESCE((
                    SELECT COUNT(*) FROM milestones m
                    JOIN projects p ON p.id = m.project_id
                    WHERE p.organisation_id = os.org_id
                ), 0)
                WHERE  os.entity_type = 'MILESTONE'
                """);
    }

    // --- tests ---

    @Test
    @DisplayName("Projects are assigned sequential friendly IDs in created_at order")
    void backfill_shouldAssignSequentialFriendlyIds_toProjects() {
        ProjectModel p2 = projectRepository.save(ProjectModel.builder()
                .name("Second").ownerId(userId).organisationId(orgId).status(ProjectStatus.ACTIVE).build());
        ProjectModel p3 = projectRepository.save(ProjectModel.builder()
                .name("Third").ownerId(userId).organisationId(orgId).status(ProjectStatus.ACTIVE).build());

        clearFriendlyIds();
        runBackfill();

        String id1 = projectRepository.findById(projectId).orElseThrow().getFriendlyId();
        String id2 = projectRepository.findById(p2.getId()).orElseThrow().getFriendlyId();
        String id3 = projectRepository.findById(p3.getId()).orElseThrow().getFriendlyId();

        assertThat(id1).isEqualTo("PRJ-001");
        assertThat(id2).isEqualTo("PRJ-002");
        assertThat(id3).isEqualTo("PRJ-003");
    }

    @Test
    @DisplayName("Jobs are assigned sequential friendly IDs in created_at order")
    void backfill_shouldAssignSequentialFriendlyIds_toJobs() {
        JobModel j1 = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Job One").status(JobStatus.NEW).createdBy(userId).build());
        JobModel j2 = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Job Two").status(JobStatus.NEW).createdBy(userId).build());

        clearFriendlyIds();
        runBackfill();

        String id1 = jobRepository.findByIdAndDeletedAtIsNull(j1.getId()).orElseThrow().getFriendlyId();
        String id2 = jobRepository.findByIdAndDeletedAtIsNull(j2.getId()).orElseThrow().getFriendlyId();

        assertThat(id1).isEqualTo("JOB-001");
        assertThat(id2).isEqualTo("JOB-002");
    }

    @Test
    @DisplayName("Milestones are assigned sequential friendly IDs in created_at order")
    void backfill_shouldAssignSequentialFriendlyIds_toMilestones() {
        MilestoneModel m1 = milestoneRepository.save(MilestoneModel.builder()
                .projectId(projectId).name("Alpha").build());
        MilestoneModel m2 = milestoneRepository.save(MilestoneModel.builder()
                .projectId(projectId).name("Beta").build());

        clearFriendlyIds();
        runBackfill();

        String id1 = milestoneRepository.findByIdAndDeletedAtIsNull(m1.getId()).orElseThrow().getFriendlyId();
        String id2 = milestoneRepository.findByIdAndDeletedAtIsNull(m2.getId()).orElseThrow().getFriendlyId();

        assertThat(id1).isEqualTo("MIL-001");
        assertThat(id2).isEqualTo("MIL-002");
    }

    @Test
    @DisplayName("org_sequences counters are updated to the total count of backfilled records")
    void backfill_shouldUpdateOrgSequenceCounters() {
        projectRepository.save(ProjectModel.builder()
                .name("Second").ownerId(userId).organisationId(orgId).status(ProjectStatus.ACTIVE).build());
        jobRepository.save(JobModel.builder()
                .projectId(projectId).title("J1").status(JobStatus.NEW).createdBy(userId).build());
        jobRepository.save(JobModel.builder()
                .projectId(projectId).title("J2").status(JobStatus.NEW).createdBy(userId).build());
        milestoneRepository.save(MilestoneModel.builder().projectId(projectId).name("M1").build());

        clearFriendlyIds();
        runBackfill();

        int projectSeq = dsl.select(ORG_SEQUENCES.LAST_VALUE).from(ORG_SEQUENCES)
                .where(ORG_SEQUENCES.ORG_ID.eq(orgId)).and(ORG_SEQUENCES.ENTITY_TYPE.eq("PROJECT"))
                .fetchSingle(ORG_SEQUENCES.LAST_VALUE);
        int jobSeq = dsl.select(ORG_SEQUENCES.LAST_VALUE).from(ORG_SEQUENCES)
                .where(ORG_SEQUENCES.ORG_ID.eq(orgId)).and(ORG_SEQUENCES.ENTITY_TYPE.eq("JOB"))
                .fetchSingle(ORG_SEQUENCES.LAST_VALUE);
        int milestoneSeq = dsl.select(ORG_SEQUENCES.LAST_VALUE).from(ORG_SEQUENCES)
                .where(ORG_SEQUENCES.ORG_ID.eq(orgId)).and(ORG_SEQUENCES.ENTITY_TYPE.eq("MILESTONE"))
                .fetchSingle(ORG_SEQUENCES.LAST_VALUE);

        assertThat(projectSeq).isEqualTo(2);   // setUp project + one more
        assertThat(jobSeq).isEqualTo(2);
        assertThat(milestoneSeq).isEqualTo(1);
    }

    @Test
    @DisplayName("Each org gets its own sequence starting at PRJ-001")
    void backfill_shouldMaintainSeparateSequences_perOrg() {
        UUID userId2 = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(userId2).email("owner2@example.com").name("Owner2").build());
        OrganisationModel org2 = organisationRepository.save(
                OrganisationModel.builder().name("Other Corp").slug("OTH").createdBy(userId2).build());
        UUID orgId2 = org2.getId();
        organisationRepository.saveMember(orgId2, userId2, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId2);

        ProjectModel p2 = projectRepository.save(ProjectModel.builder()
                .name("Org2 Project").ownerId(userId2).organisationId(orgId2)
                .status(ProjectStatus.ACTIVE).build());

        clearFriendlyIds();
        runBackfill();

        String org1Id = projectRepository.findById(projectId).orElseThrow().getFriendlyId();
        String org2Id = projectRepository.findById(p2.getId()).orElseThrow().getFriendlyId();

        assertThat(org1Id).isEqualTo("PRJ-001");
        assertThat(org2Id).isEqualTo("PRJ-001");  // separate sequence
    }

    @Test
    @DisplayName("Backfill is idempotent — already-set friendly IDs are not overwritten")
    void backfill_shouldBeIdempotent_skippingAlreadySetIds() {
        // Project from setUp already has PRJ-001 (set by FriendlyIdService via seedForOrg + first save)
        // Set it manually so we know the value
        dsl.update(PROJECTS).set(PROJECTS.FRIENDLY_ID, "PRJ-042")
                .where(PROJECTS.ID.eq(projectId)).execute();

        // Second project has null friendly_id
        ProjectModel p2 = projectRepository.save(ProjectModel.builder()
                .name("Second").ownerId(userId).organisationId(orgId).status(ProjectStatus.ACTIVE).build());
        dsl.update(PROJECTS).setNull(PROJECTS.FRIENDLY_ID).where(PROJECTS.ID.eq(p2.getId())).execute();

        runBackfill();

        String existing = projectRepository.findById(projectId).orElseThrow().getFriendlyId();
        String backfilled = projectRepository.findById(p2.getId()).orElseThrow().getFriendlyId();

        assertThat(existing).isEqualTo("PRJ-042");  // untouched
        assertThat(backfilled).isEqualTo("PRJ-001"); // only null row backfilled
    }
}
