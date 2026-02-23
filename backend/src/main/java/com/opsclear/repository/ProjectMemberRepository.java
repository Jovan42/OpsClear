package com.opsclear.repository;

import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.PROJECT_MEMBERS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static java.util.List.of;

@Repository
@RequiredArgsConstructor
public class ProjectMemberRepository {

    private static final Field<String> MEMBER_NAME = USERS.NAME.as("member_name");
    private static final Field<String> MEMBER_EMAIL = USERS.EMAIL.as("member_email");

    private final DSLContext dsl;

    public Optional<ProjectMemberModel> findByProjectIdAndUserId(UUID projectId, UUID userId) {
        return selectWithUser()
                .where(PROJECT_MEMBERS.PROJECT_ID.eq(projectId))
                .and(PROJECT_MEMBERS.USER_ID.eq(userId))
                .fetchOptional()
                .map(this::toModel);
    }

    public List<ProjectMemberModel> findByProjectId(UUID projectId) {
        return selectWithUser()
                .where(PROJECT_MEMBERS.PROJECT_ID.eq(projectId))
                .fetch()
                .map(this::toModel);
    }

    public boolean existsByProjectIdAndUserId(UUID projectId, UUID userId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(PROJECT_MEMBERS)
                        .where(PROJECT_MEMBERS.PROJECT_ID.eq(projectId))
                        .and(PROJECT_MEMBERS.USER_ID.eq(userId)));
    }

    public ProjectMemberModel save(ProjectMemberModel member) {
        if (member.getId() == null) {
            UUID id = dsl.insertInto(PROJECT_MEMBERS)
                    .set(PROJECT_MEMBERS.PROJECT_ID, member.getProjectId())
                    .set(PROJECT_MEMBERS.USER_ID, member.getUserId())
                    .set(PROJECT_MEMBERS.ROLE, member.getRole().name())
                    .set(PROJECT_MEMBERS.JOINED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .returning(PROJECT_MEMBERS.ID)
                    .fetchOne()
                    .getId();
            return findById(id).orElseThrow();
        }
        dsl.update(PROJECT_MEMBERS)
                .set(PROJECT_MEMBERS.ROLE, member.getRole().name())
                .where(PROJECT_MEMBERS.ID.eq(member.getId()))
                .execute();
        return findById(member.getId()).orElseThrow();
    }

    public void delete(UUID memberId) {
        dsl.deleteFrom(PROJECT_MEMBERS)
                .where(PROJECT_MEMBERS.ID.eq(memberId))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(PROJECT_MEMBERS).execute();
    }

    public Optional<ProjectMemberModel> findById(UUID id) {
        return selectWithUser()
                .where(PROJECT_MEMBERS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    private SelectOnConditionStep<Record> selectWithUser() {
        return dsl.select(of(
                        PROJECT_MEMBERS.ID,
                        PROJECT_MEMBERS.PROJECT_ID,
                        PROJECT_MEMBERS.USER_ID,
                        PROJECT_MEMBERS.ROLE,
                        PROJECT_MEMBERS.JOINED_AT,
                        MEMBER_NAME,
                        MEMBER_EMAIL))
                .from(PROJECT_MEMBERS)
                .join(USERS).on(PROJECT_MEMBERS.USER_ID.eq(USERS.ID));
    }

    private ProjectMemberModel toModel(Record r) {
        return ProjectMemberModel.builder()
                .id(r.get(PROJECT_MEMBERS.ID))
                .projectId(r.get(PROJECT_MEMBERS.PROJECT_ID))
                .userId(r.get(PROJECT_MEMBERS.USER_ID))
                .role(ProjectMemberRole.valueOf(r.get(PROJECT_MEMBERS.ROLE)))
                .joinedAt(toInstant(r.get(PROJECT_MEMBERS.JOINED_AT)))
                .userName(r.get(MEMBER_NAME))
                .userEmail(r.get(MEMBER_EMAIL))
                .build();
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
