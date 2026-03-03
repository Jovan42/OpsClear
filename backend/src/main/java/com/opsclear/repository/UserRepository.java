package com.opsclear.repository;

import com.opsclear.model.UserModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.USERS;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private UserModel toModel(Record r) {
        return UserModel.builder()
                .id(r.get(USERS.ID))
                .email(r.get(USERS.EMAIL))
                .name(r.get(USERS.NAME))
                .createdAt(toInstant(r.get(USERS.CREATED_AT)))
                .lastLoginAt(toInstant(r.get(USERS.LAST_LOGIN_AT)))
                .build();
    }

    public Optional<UserModel> findById(UUID id) {
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.NAME, USERS.CREATED_AT, USERS.LAST_LOGIN_AT)
                .from(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public List<UserModel> searchByEmail(String emailPrefix, int limit) {
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.NAME, USERS.CREATED_AT, USERS.LAST_LOGIN_AT)
                .from(USERS)
                .where(USERS.EMAIL.likeIgnoreCase(emailPrefix + "%"))
                .orderBy(USERS.EMAIL.asc())
                .limit(limit)
                .fetch()
                .map(this::toModel);
    }

    public UserModel save(UserModel user) {
        Instant now = Instant.now();
        dsl.insertInto(USERS)
                .set(USERS.ID, user.getId())
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.NAME, user.getName())
                .set(USERS.CREATED_AT, toLocalDateTime(now))
                .set(USERS.LAST_LOGIN_AT, toLocalDateTime(now))
                .onConflict(USERS.ID)
                .doUpdate()
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.NAME, user.getName())
                .set(USERS.LAST_LOGIN_AT, toLocalDateTime(now))
                .execute();
        return findById(user.getId()).orElseThrow();
    }

    public void deleteAll() {
        dsl.deleteFrom(USERS).execute();
    }
}
