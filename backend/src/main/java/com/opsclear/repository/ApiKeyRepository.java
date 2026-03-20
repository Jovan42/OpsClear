package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.ApiKeysRecord;
import com.opsclear.model.ApiKeyModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.API_KEYS;

@Repository
@RequiredArgsConstructor
public class ApiKeyRepository {

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private ApiKeyModel toModel(ApiKeysRecord r) {
        return ApiKeyModel.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .name(r.getName())
                .keyHash(r.getKeyHash())
                .keyPrefix(r.getKeyPrefix())
                .createdAt(toInstant(r.getCreatedAt()))
                .lastUsedAt(toInstant(r.getLastUsedAt()))
                .expiresAt(toInstant(r.getExpiresAt()))
                .revokedAt(toInstant(r.getRevokedAt()))
                .build();
    }

    public ApiKeyModel save(ApiKeyModel key) {
        UUID id = dsl.insertInto(API_KEYS)
                .set(API_KEYS.USER_ID, key.getUserId())
                .set(API_KEYS.NAME, key.getName())
                .set(API_KEYS.KEY_HASH, key.getKeyHash())
                .set(API_KEYS.KEY_PREFIX, key.getKeyPrefix())
                .set(API_KEYS.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .set(API_KEYS.EXPIRES_AT, toLocalDateTime(key.getExpiresAt()))
                .returning(API_KEYS.ID)
                .fetchSingle()
                .getId();
        return findById(id).orElseThrow();
    }

    public Optional<ApiKeyModel> findById(UUID id) {
        return dsl.selectFrom(API_KEYS)
                .where(API_KEYS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public Optional<ApiKeyModel> findByKeyHash(String keyHash) {
        return dsl.selectFrom(API_KEYS)
                .where(API_KEYS.KEY_HASH.eq(keyHash))
                .fetchOptional()
                .map(this::toModel);
    }

    public void updateLastUsedAt(UUID id) {
        dsl.update(API_KEYS)
                .set(API_KEYS.LAST_USED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(API_KEYS.ID.eq(id))
                .execute();
    }

    public List<ApiKeyModel> findActiveByUserId(UUID userId) {
        return dsl.selectFrom(API_KEYS)
                .where(API_KEYS.USER_ID.eq(userId))
                .and(API_KEYS.REVOKED_AT.isNull())
                .orderBy(API_KEYS.CREATED_AT.desc())
                .fetch()
                .map(this::toModel);
    }

    public void revoke(UUID id) {
        dsl.update(API_KEYS)
                .set(API_KEYS.REVOKED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(API_KEYS.ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(API_KEYS).execute();
    }
}
