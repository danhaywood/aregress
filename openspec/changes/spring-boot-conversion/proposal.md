## Why

aregress is a plain-Java Picocli CLI (fat JAR via maven-shade) with all configuration passed as command-line options. Upcoming features will need richer, layered configuration (defaults, environment overrides, profiles), and the owner wants to manage that the familiar Spring Boot way — `application.yml`, environment variables, `@ConfigurationProperties`. Converting now lays that foundation before the config surface grows.

## What Changes

- **Convert aregress to a Spring Boot 4.x application** (no web server — a CLI runner). Picocli is retained and integrated with Spring via `picocli-spring-boot-starter`, so the existing options, help, and exit codes are preserved while commands become Spring-managed beans (with dependency injection).
- **Introduce externalized configuration**: settings (app/cfct base URLs, timeouts, cfct username, etc.) are bound from Spring Boot's config sources — a bundled `application.yml`, environment variables, and system properties — with **CLI options taking highest precedence** when supplied. Sensible defaults ship in `application.yml`.
- **Replace the packaging**: `spring-boot-maven-plugin` (repackage → executable jar) replaces `maven-shade-plugin`. The `aregress` launcher and `java -jar` invocation are preserved.
- **No change to observable replay/compare/import behaviour** — same commands, same logging, same exit codes (`0`/`1`/`2`). This is a refactor plus a new configuration capability; functional features that *use* the new config come later.

## Capabilities

### New Capabilities

- `app-configuration`: load settings from Spring Boot externalized configuration (bundled `application.yml`, env vars, system properties) with a defined precedence; CLI options override.

### Modified Capabilities
<!-- none — the CLI's observable behaviour (options, exit codes, replay/compare/import) is preserved; only how it is bootstrapped and how defaults are sourced changes (see design). -->

## Impact

- **Build**: `pom.xml` adopts `spring-boot-starter-parent` 4.x, `spring-boot-starter` (core, no web), `picocli-spring-boot-starter`, and `spring-boot-maven-plugin`; drops `maven-shade-plugin`. Possible Java-baseline bump (Spring Boot 4.x — confirm 17 vs 21) affecting the launcher's version check.
- **Code**: `Main` becomes a `@SpringBootApplication` bootstrap; the command class becomes a Spring-managed `@Command` bean; a `@ConfigurationProperties` type holds the externalized settings; exit-code propagation wired so non-zero codes still reach the shell.
- **Runtime**: heavier startup and a larger jar than the bare CLI — an accepted trade-off for Spring Boot's config management.
- **Docs**: README (build/run, config precedence, `application.yml`) and the `aregress` launcher updated as needed.
- **Out of scope**: any new functional feature that consumes the new config; changes to the replay loop, cfct comparison, or import flow.
