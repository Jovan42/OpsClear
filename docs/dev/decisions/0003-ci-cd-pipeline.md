# ADR-0003: CI/CD Pipeline with GitHub Actions

**Status:** Proposed
**Date:** 2026-02-05
**Author:** Jovan

---

## Context

OpsClear needs automated quality checks to:
- Prevent broken code from being merged to main
- Ensure tests pass before merge
- Enforce code style consistency
- Provide fast feedback on pull requests

As a solo developer working with AI assistance, automated checks are essential to catch issues early.

### Requirements

- Run on every PR to main
- Block merge if checks fail
- Fast feedback (under 5 minutes)
- No cost (free tier sufficient)
- Simple to maintain

---

## Decision

**Use GitHub Actions for CI/CD with build, test, and lint checks.**

### Pipeline Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                     Pull Request to main                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
     ┌────────────────────────┼────────────────────────┐
     │                        │                        │
     ▼                        ▼                        ▼
┌──────────┐           ┌──────────────┐         ┌──────────────┐
│  Build   │           │  Checkstyle  │         │  Unit Tests  │
│          │           │              │         │   (fast)     │
│ Compile  │           │  Code style  │         │   ~5 sec     │
└──────────┘           └──────────────┘         └──────────────┘
                                                       │
                                                       ▼
                                               ┌──────────────┐
                                               │ Integration  │
                                               │    Tests     │
                                               │  (slower)    │
                                               │   ~25 sec    │
                                               └──────────────┘
                              │
                              ▼
                    ┌────────────────┐
                    │  All passed?   │
                    │                │
                    │  ✓ Can merge   │
                    │  ✗ Blocked     │
                    └────────────────┘
```

### Jobs

| Job | Purpose | Commands | Duration |
|-----|---------|----------|----------|
| **Build** | Compile code | `./gradlew build -x test` | ~15s |
| **Unit Tests** | Fast tests, no containers | `./gradlew test --tests '*Test' --exclude-task '*IntegrationTest'` | ~5s |
| **Integration Tests** | Tests with Testcontainers | `./gradlew test --tests '*IntegrationTest'` | ~25s |
| **Checkstyle** | Enforce code style | `./gradlew checkstyleMain checkstyleTest` | ~10s |

### Test Separation Strategy

Tests are separated by naming convention:
- `*Test.java` - Unit tests (mocked dependencies, fast)
- `*IntegrationTest.java` - Integration tests (Testcontainers, slower)

Integration tests run **after** unit tests pass (fail fast).

### Triggers

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

---

## Checkstyle Configuration

Using Google Java Style with minor modifications:
- Indentation: 4 spaces (instead of 2)
- Line length: 120 characters
- Allow `@author` tags

### Why Checkstyle?

- Built into Gradle (no extra tools)
- Widely used, well-documented
- Google Java Style is a good baseline
- Fast execution

---

## Branch Protection Rules

Configure in GitHub Settings > Branches > main:

| Rule | Value | Why |
|------|-------|-----|
| Require status checks | ✓ | Block merge if CI fails |
| Required checks | `Build & Test`, `Checkstyle` | Both must pass |
| Require branches up to date | ✓ | Ensure PR is current with main |
| Require PR reviews | ✗ | Solo project, no reviewers |
| Allow force pushes | ✗ | Protect history |
| Allow deletions | ✗ | Protect main |

---

## Workflow File

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    name: Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      - run: chmod +x backend/gradlew
      - run: ./gradlew build -x test
        working-directory: backend

  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      - run: chmod +x backend/gradlew
      - run: ./gradlew test -PexcludeIntegrationTests
        working-directory: backend

  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      - run: chmod +x backend/gradlew
      - run: ./gradlew test -PonlyIntegrationTests
        working-directory: backend

  lint:
    name: Checkstyle
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: chmod +x backend/gradlew
      - run: ./gradlew checkstyleMain checkstyleTest
        working-directory: backend
```

Note: The `build.gradle` needs configuration to support `-PexcludeIntegrationTests` and `-PonlyIntegrationTests` flags.

---

## Alternatives Considered

### Alternative 1: Jenkins

**Pros:**
- Full control
- Highly customizable
- Self-hosted

**Cons:**
- Requires server to run
- Complex setup and maintenance
- Overkill for this project

**Why rejected:** Too much infrastructure for a solo project.

### Alternative 2: CircleCI

**Pros:**
- Good free tier
- Fast builds
- Good UI

**Cons:**
- Another account/service
- Less integrated with GitHub
- Config in separate format

**Why rejected:** GitHub Actions is more integrated, same features.

### Alternative 3: No Linting

**Pros:**
- Simpler pipeline
- Faster builds

**Cons:**
- Inconsistent code style
- Harder to read code later
- AI might generate inconsistent styles

**Why rejected:** Code consistency is valuable, especially with AI assistance.

---

## Consequences

### Positive

- **Automated quality gate** - no broken code in main
- **Fast feedback** - know within minutes if PR is good
- **Consistent style** - Checkstyle enforces formatting
- **Free** - GitHub Actions free for public repos
- **Simple** - YAML config, no infrastructure

### Negative

- **Initial setup** - need to configure Checkstyle rules
- **Potential friction** - may need to fix style issues
- **Test container startup** - integration tests add ~20s

### Neutral

- **Learning curve** - GitHub Actions syntax is straightforward
- **Maintenance** - may need to update actions versions occasionally

---

## Implementation Plan

1. Add Checkstyle plugin to `build.gradle`
2. Create `checkstyle.xml` with Google style + modifications
3. Add test filtering config to `build.gradle` (unit vs integration)
4. Create `.github/workflows/ci.yml`
5. Test workflow on a PR
6. Configure branch protection rules in GitHub
7. Document in CONTRIBUTING.md

---

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Gradle Checkstyle Plugin](https://docs.gradle.org/current/userguide/checkstyle_plugin.html)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [GitHub Branch Protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
