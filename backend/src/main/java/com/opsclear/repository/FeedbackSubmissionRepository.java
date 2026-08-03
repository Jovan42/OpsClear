package com.opsclear.repository;

import com.opsclear.model.FeedbackStatus;
import com.opsclear.model.FeedbackSubmissionModel;
import com.opsclear.model.FeedbackType;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.FEEDBACK_SUBMISSIONS;
import static com.opsclear.generated.jooq.Tables.ORGANISATIONS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static java.util.List.of;

@Repository
@RequiredArgsConstructor
public class FeedbackSubmissionRepository {

    private static final Field<String> ORG_NAME = ORGANISATIONS.NAME.as("org_name");
    private static final Field<String> SUBMITTER_NAME = USERS.NAME.as("submitter_name");
    private static final Field<String> SUBMITTER_EMAIL = USERS.EMAIL.as("submitter_email");

    private final DSLContext dsl;

    public FeedbackSubmissionModel insert(UUID orgId, UUID submittedBy, FeedbackType type,
                                           String title, String description) {
        UUID id = dsl.insertInto(FEEDBACK_SUBMISSIONS)
                .set(FEEDBACK_SUBMISSIONS.ORG_ID, orgId)
                .set(FEEDBACK_SUBMISSIONS.SUBMITTED_BY, submittedBy)
                .set(FEEDBACK_SUBMISSIONS.TYPE, type.name())
                .set(FEEDBACK_SUBMISSIONS.TITLE, title)
                .set(FEEDBACK_SUBMISSIONS.DESCRIPTION, description)
                .returning(FEEDBACK_SUBMISSIONS.ID)
                .fetchSingle()
                .getId();
        return findById(id).orElseThrow();
    }

    public Optional<FeedbackSubmissionModel> findById(UUID id) {
        return selectWithJoins()
                .where(FEEDBACK_SUBMISSIONS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public Optional<FeedbackSubmissionModel> findByIdAndOrgId(UUID id, UUID orgId) {
        return selectWithJoins()
                .where(FEEDBACK_SUBMISSIONS.ID.eq(id))
                .and(FEEDBACK_SUBMISSIONS.ORG_ID.eq(orgId))
                .fetchOptional()
                .map(this::toModel);
    }

    public List<FeedbackSubmissionModel> findBySubmittedBy(UUID userId) {
        return selectWithJoins()
                .where(FEEDBACK_SUBMISSIONS.SUBMITTED_BY.eq(userId))
                .orderBy(FEEDBACK_SUBMISSIONS.CREATED_AT.desc())
                .fetch()
                .map(this::toModel);
    }

    public List<FeedbackSubmissionModel> findAll() {
        return selectWithJoins()
                .orderBy(FEEDBACK_SUBMISSIONS.CREATED_AT.desc())
                .fetch()
                .map(this::toModel);
    }

    public void updateStatus(UUID id, FeedbackStatus status) {
        dsl.update(FEEDBACK_SUBMISSIONS)
                .set(FEEDBACK_SUBMISSIONS.STATUS, status.name())
                .where(FEEDBACK_SUBMISSIONS.ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(FEEDBACK_SUBMISSIONS).execute();
    }

    private SelectOnConditionStep<Record> selectWithJoins() {
        return dsl.select(of(
                        FEEDBACK_SUBMISSIONS.ID,
                        FEEDBACK_SUBMISSIONS.ORG_ID,
                        ORG_NAME,
                        FEEDBACK_SUBMISSIONS.SUBMITTED_BY,
                        SUBMITTER_NAME,
                        SUBMITTER_EMAIL,
                        FEEDBACK_SUBMISSIONS.TYPE,
                        FEEDBACK_SUBMISSIONS.TITLE,
                        FEEDBACK_SUBMISSIONS.DESCRIPTION,
                        FEEDBACK_SUBMISSIONS.STATUS,
                        FEEDBACK_SUBMISSIONS.CREATED_AT))
                .from(FEEDBACK_SUBMISSIONS)
                .join(ORGANISATIONS).on(FEEDBACK_SUBMISSIONS.ORG_ID.eq(ORGANISATIONS.ID))
                .join(USERS).on(FEEDBACK_SUBMISSIONS.SUBMITTED_BY.eq(USERS.ID));
    }

    private FeedbackSubmissionModel toModel(Record r) {
        return FeedbackSubmissionModel.builder()
                .id(r.get(FEEDBACK_SUBMISSIONS.ID))
                .orgId(r.get(FEEDBACK_SUBMISSIONS.ORG_ID))
                .orgName(r.get(ORG_NAME))
                .submittedBy(r.get(FEEDBACK_SUBMISSIONS.SUBMITTED_BY))
                .submitterName(r.get(SUBMITTER_NAME))
                .submitterEmail(r.get(SUBMITTER_EMAIL))
                .type(FeedbackType.valueOf(r.get(FEEDBACK_SUBMISSIONS.TYPE)))
                .title(r.get(FEEDBACK_SUBMISSIONS.TITLE))
                .description(r.get(FEEDBACK_SUBMISSIONS.DESCRIPTION))
                .status(FeedbackStatus.valueOf(r.get(FEEDBACK_SUBMISSIONS.STATUS)))
                .createdAt(r.get(FEEDBACK_SUBMISSIONS.CREATED_AT).toInstant())
                .build();
    }
}
