package com.opsclear.repository;

import com.opsclear.model.FriendlyIdEntityType;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SEQUENCES;
import static com.opsclear.generated.jooq.Tables.ORG_SETTINGS;

@Repository
@RequiredArgsConstructor
public class FriendlyIdRepository {

    private final DSLContext dsl;

    /**
     * Atomically increments the sequence counter for the given org and entity type,
     * returning the new value. Safe under concurrent inserts — UPDATE is row-locked by Postgres.
     */
    public int incrementAndGet(UUID orgId, FriendlyIdEntityType entityType) {
        return dsl.update(ORG_SEQUENCES)
                .set(ORG_SEQUENCES.LAST_VALUE, ORG_SEQUENCES.LAST_VALUE.add(1))
                .where(ORG_SEQUENCES.ORG_ID.eq(orgId))
                .and(ORG_SEQUENCES.ENTITY_TYPE.eq(entityType.name()))
                .returning(ORG_SEQUENCES.LAST_VALUE)
                .fetchSingle()
                .getLastValue();
    }

    /**
     * Returns the configured prefix for the given entity type in this org.
     */
    public String getPrefix(UUID orgId, FriendlyIdEntityType entityType) {
        return dsl.select(prefixField(entityType))
                .from(ORG_SETTINGS)
                .where(ORG_SETTINGS.ORG_ID.eq(orgId))
                .fetchSingle(prefixField(entityType));
    }

    /**
     * Seeds org_settings (with defaults) and org_sequences (zeroed) for a newly created org.
     * Called inside the same transaction as org creation.
     */
    public void seedForOrg(UUID orgId) {
        dsl.insertInto(ORG_SETTINGS)
                .set(ORG_SETTINGS.ORG_ID, orgId)
                .onDuplicateKeyIgnore()
                .execute();

        for (FriendlyIdEntityType type : FriendlyIdEntityType.values()) {
            dsl.insertInto(ORG_SEQUENCES)
                    .set(ORG_SEQUENCES.ORG_ID, orgId)
                    .set(ORG_SEQUENCES.ENTITY_TYPE, type.name())
                    .set(ORG_SEQUENCES.LAST_VALUE, 0)
                    .onDuplicateKeyIgnore()
                    .execute();
        }
    }

    private Field<String> prefixField(FriendlyIdEntityType type) {
        return switch (type) {
            case PROJECT -> ORG_SETTINGS.PROJECT_PREFIX;
            case JOB -> ORG_SETTINGS.JOB_PREFIX;
            case MILESTONE -> ORG_SETTINGS.MILESTONE_PREFIX;
        };
    }
}
