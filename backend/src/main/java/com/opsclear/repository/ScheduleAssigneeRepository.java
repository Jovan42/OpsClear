package com.opsclear.repository;

import com.opsclear.dto.ScheduleAssigneeResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.SCHEDULE_ASSIGNEES;
import static com.opsclear.generated.jooq.Tables.USERS;

@Repository
@RequiredArgsConstructor
public class ScheduleAssigneeRepository {

    private final DSLContext dsl;

    public List<ScheduleAssigneeResponse> findByScheduleId(UUID scheduleId) {
        return dsl.select(
                        SCHEDULE_ASSIGNEES.USER_ID,
                        USERS.NAME.as("user_name"),
                        SCHEDULE_ASSIGNEES.ORDER)
                .from(SCHEDULE_ASSIGNEES)
                .join(USERS).on(SCHEDULE_ASSIGNEES.USER_ID.eq(USERS.ID))
                .where(SCHEDULE_ASSIGNEES.SCHEDULE_ID.eq(scheduleId))
                .orderBy(SCHEDULE_ASSIGNEES.ORDER.asc())
                .fetch()
                .map(this::toResponse);
    }

    public void replaceForSchedule(UUID scheduleId, List<UUID> orderedUserIds) {
        dsl.deleteFrom(SCHEDULE_ASSIGNEES)
                .where(SCHEDULE_ASSIGNEES.SCHEDULE_ID.eq(scheduleId))
                .execute();
        for (int i = 0; i < orderedUserIds.size(); i++) {
            dsl.insertInto(SCHEDULE_ASSIGNEES)
                    .set(SCHEDULE_ASSIGNEES.SCHEDULE_ID, scheduleId)
                    .set(SCHEDULE_ASSIGNEES.USER_ID, orderedUserIds.get(i))
                    .set(SCHEDULE_ASSIGNEES.ORDER, i)
                    .execute();
        }
    }

    public List<UUID> deleteByUserId(UUID userId) {
        List<UUID> affectedScheduleIds = dsl.select(SCHEDULE_ASSIGNEES.SCHEDULE_ID)
                .from(SCHEDULE_ASSIGNEES)
                .where(SCHEDULE_ASSIGNEES.USER_ID.eq(userId))
                .fetch()
                .map(r -> r.get(SCHEDULE_ASSIGNEES.SCHEDULE_ID));

        if (!affectedScheduleIds.isEmpty()) {
            dsl.deleteFrom(SCHEDULE_ASSIGNEES)
                    .where(SCHEDULE_ASSIGNEES.USER_ID.eq(userId))
                    .execute();
        }

        List<UUID> nowEmpty = new ArrayList<>();
        for (UUID scheduleId : affectedScheduleIds) {
            int remaining = dsl.fetchCount(
                    dsl.selectFrom(SCHEDULE_ASSIGNEES)
                            .where(SCHEDULE_ASSIGNEES.SCHEDULE_ID.eq(scheduleId)));
            if (remaining == 0) {
                nowEmpty.add(scheduleId);
            }
        }
        return nowEmpty;
    }

    public void deleteAll() {
        dsl.deleteFrom(SCHEDULE_ASSIGNEES).execute();
    }

    private ScheduleAssigneeResponse toResponse(Record r) {
        return ScheduleAssigneeResponse.builder()
                .userId(r.get(SCHEDULE_ASSIGNEES.USER_ID))
                .userName(r.get("user_name", String.class))
                .order(r.get(SCHEDULE_ASSIGNEES.ORDER))
                .build();
    }
}
