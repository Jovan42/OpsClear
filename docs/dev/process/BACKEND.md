# Backend

Spring Boot 3.x · Java 21 · jOOQ · Flyway · PostgreSQL 16

---

## Layering

### Controllers
- `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor` on every controller
- Extract caller identity with `SecurityUtils.resolveUserId(auth)` — never parse the JWT directly
- Resolve friendly IDs in path params using `FriendlyIdResolver` (`resolveProject`, `resolveJob`, `resolveMilestone`, etc.)
- Return `ResponseEntity` with explicit status codes: `201` for POST, `204` for DELETE, `200` for GET/PUT
- Apply `@Valid` on request body parameters
- Response mapping happens in the controller: `.stream().map(XxxResponse::from).toList()`
- `@RequiresAddon(AddonCode.XXX)` goes on individual methods, not on the class
- Variable naming: `callerId` / `userId` for the authenticated user, `pid` / `jid` / `mid` for resolved entity IDs
- No business logic — delegate everything to the service layer

### Services
- `@Service`, `@RequiredArgsConstructor`, `@Slf4j` on every service
- Annotate write methods `@Transactional`, reads `@Transactional(readOnly = true)`
- All business logic and permission checks live here
- All error messages live in `ErrorMessages` nested classes — never inline strings in `throw` statements
- `log.info(...)` on every significant mutation (create, update, soft delete)
- Builder pattern for constructing model objects: `XxxModel.builder()...build()`
- Private helper methods should throw on invalid state — never return null or Optional and leave the check to the caller
- Guards (private `requireXxx` methods) are grouped at the bottom of the class under a `// --- Guards ---` comment

### Repositories
- `@Repository`, `@RequiredArgsConstructor`
- Only dependency is `private final DSLContext dsl`
- `toModel(XxxRecord r)` private method converts the jOOQ record to a model — all mapping in one place
- `save()` checks `id == null` to decide insert vs update; insert uses `.returning(TABLE.ID).fetchSingle()` then re-fetches the full record
- Timestamps use `LocalDateTime.now(ZoneOffset.UTC)` for writes, converted via private `toInstant` / `toLocalDateTime` helpers
- Filter soft-deleted rows with `.and(TABLE.DELETED_AT.isNull())` on every active-record query
- Method naming: `findByIdAndDeletedAtIsNull`, `findActiveByXxx`, `save`, `softDelete`, `deleteAll`

### Models
- Full Lombok stack: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
- `softDelete()` sets `deletedAt = Instant.now()`, `isDeleted()` checks it
- Collection fields use `@Builder.Default` to initialise to an empty list
- Fields resolved via JOIN (not DB columns) get a `// resolved via JOIN, not a DB column` comment

### DTOs
- Request DTOs: same Lombok stack, Jakarta validation with inline messages (`@NotBlank(message = "Title is required")`)
- Response DTOs: static factory method `public static XxxResponse from(XxxModel model)`, builder-based construction inside it

---

## jOOQ patterns

- Soft delete filter: always append `.and(TABLE.DELETED_AT.isNull())` to active-record queries
- Friendly ID lookup: use `FriendlyIdResolver` for path params that accept `TPL-001` style IDs
- Null-safe optional conditions: use `DSL.noCondition()` as the else branch, `DSL.falseCondition()` when the filter must match nothing
- After schema changes run `./gradlew generateJooq` to regenerate jOOQ classes

---

## Flyway migrations

- File naming: `V{n}__{description}.sql` — increment `n` by 1 from the last migration
- Description uses underscores: `V020__org_level_templates.sql`
- Never modify an existing migration — always add a new one
- After adding a migration, regenerate jOOQ: `./gradlew generateJooq`

---

## Soft delete

Entities have a `deleted_at` timestamp. To delete a record call `entity.softDelete()` — never `repository.delete()`.

---

## Feature gating

Use `@RequiresAddon(AddonCode.SOME_ADDON)` on controller methods to gate features behind a subscription addon. Returns 403 if the org does not have the addon active.

---

## Error handling

All error message strings live in `ErrorMessages` nested classes (e.g. `ErrorMessages.Job.NOT_FOUND`). Never write a message string inline in a `throw` statement. Add a new constant to the appropriate nested class when introducing a new error.

---

## Testing

### Unit tests
- `@ExtendWith(MockitoExtension.class)` — no Spring context
- `@Mock` for all dependencies, manually instantiate the service in `@BeforeEach`
- Cover all service methods — aim for ~100% instruction coverage
- Use AssertJ (`assertThat`, `assertThatThrownBy`) and `ArgumentCaptor`
- All test classes and methods annotated with `@DisplayName`

### Integration tests
- Live in `src/test/java/com/opsclear/integration/`
- `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- `@BeforeEach` cleans state via `repository.deleteAll()`
- Mock JWT: `.with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("email", "...")))`
- Assert HTTP status + `jsonPath()` + database state after mutations
- URL strings: always use `ApiPaths` helper methods — never raw string concatenation
- Coverage target: ~100% instruction coverage on all new lines

```bash
./gradlew test                              # all tests
./gradlew test -PexcludeIntegrationTests   # unit only
./gradlew test -PonlyIntegrationTests      # integration only
./gradlew test --tests "com.opsclear.service.SomeServiceTest"
```

---

## Checkstyle

120-character line limit. Run `./gradlew checkstyleMain` to verify before opening a PR.
