# ADR-0006: DTO Mapping Strategy — Manual `from()` over MapStruct

**Status:** Accepted
**Date:** 2026-02-24
**Author:** Jovan Manojlovic

## Context

As the codebase grows, DTOs must be converted to and from the model layer (jOOQ plain POJOs).
The question is whether to use a mapping library (MapStruct) or stick with manual factory methods.

The current pattern uses a static `from()` factory method on each response DTO:

```java
public static ProjectResponse from(ProjectModel project) {
    return ProjectResponse.builder()
            .id(project.getId())
            .name(project.getName())
            ...
            .build();
}
```

This pattern was introduced early and is consistent across all existing DTOs
(`ProjectResponse`, `ProjectMemberResponse`).

## Decision

Keep manual `from()` factory methods. Do not introduce MapStruct.

## Alternatives Considered

### Alternative 1: MapStruct

A compile-time annotation processor that generates mapping code automatically.

**Pros:**
- Reduces boilerplate for objects with many fields
- Compile-time safety: warns about unmapped fields
- Zero runtime overhead (generated code, not reflection)

**Cons:**
- Adds annotation processing to the build — increases build time and IDE complexity
- Works best with JPA entities; the project uses plain jOOQ POJOs which already have explicit constructors and builders
- Overkill for the current mapping complexity (mostly 1-to-1 field copies)
- Introduces a dependency and tooling requirement the team must learn
- Magic code generation obscures what the mapping actually does

**Why rejected:** The mappings are simple and explicit. The overhead of adding annotation
processing outweighs the boilerplate reduction at this scale.

### Alternative 2: ModelMapper (runtime reflection)

A runtime reflection-based mapper.

**Pros:**
- Zero boilerplate

**Cons:**
- Runtime overhead and potential `ClassCastException` bugs
- No compile-time safety
- Fragile with nested objects and custom conversions

**Why rejected:** Runtime reflection adds fragility with no benefit over a 5-line `from()` method.

## Consequences

### Positive

- No additional dependencies or build tooling
- Mapping logic is explicit, readable, and easy to debug
- Full control when model and DTO fields diverge (e.g., computed fields, type conversions)
- Consistent with the existing codebase pattern

### Negative

- Manually written `from()` methods for each new DTO (low effort, ~5–10 lines each)
- No automatic warning if a new model field is added but the DTO is not updated

### Neutral

- All response DTOs carry a `static from(Model m)` factory method by convention
- Request DTOs do not need `from()` methods — they are consumed by the service layer directly

## Implementation Notes

- Convention: all response DTOs expose `static ResponseDto from(Model model)`
- Use Lombok `@Builder` on response DTOs to keep the `from()` method concise
- When a model and DTO diverge (e.g., flattening, renaming), handle it explicitly in `from()`

## References

- [MapStruct documentation](https://mapstruct.org/)
- Existing examples: `ProjectResponse.from()`, `ProjectMemberResponse.from()`
