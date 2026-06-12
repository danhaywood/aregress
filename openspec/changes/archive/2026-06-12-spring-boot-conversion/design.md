## Context

aregress today: a plain-Java Picocli CLI. `Main` is a `@Command implements Callable<Integer>`; `main()` does `System.exit(new CommandLine(new Main()).execute(args))`. Packaged as a fat JAR via `maven-shade-plugin`. All config is CLI options with hardcoded `defaultValue`s. Dependencies: Playwright, Picocli, Gson; Java 17.

Target: a Spring Boot 4.x CLI app (no web server) so configuration can come from Spring's externalized sources, ahead of features that need it. Observable behaviour (options, help, logging, exit codes, replay/compare/import) is unchanged.

## Goals / Non-Goals

**Goals:**
- Bootstrap via Spring Boot 4.x; retain Picocli (options/help/exit codes intact) through `picocli-spring-boot-starter`.
- Externalized config (`application.yml` + env + system properties) bound to a `@ConfigurationProperties` type; CLI options override.
- Executable jar via `spring-boot-maven-plugin`; preserve the `aregress` launcher and `java -jar` usage.
- Keep stdout clean (the `[step N] …` lines), i.e. quiet Spring startup.

**Non-Goals:**
- New functional features that consume the config (later).
- Changing the replay loop, cfct comparison, or import flow.
- Putting secrets (passwords) into `application.yml` — those stay CLI/env only.

## Decisions

### Spring Boot 4.x, no web; Picocli retained via `picocli-spring-boot-starter`
Use `spring-boot-starter` (core only — no `-web`; set `spring.main.web-application-type=none`). Add `picocli-spring-boot-starter` so the command is a Spring bean and Picocli resolves beans via Spring's `IFactory` (enables `@ConfigurationProperties` injection into the command). Alternatives — Spring Shell (interactive, wrong shape), or hand-wiring a `CommandLineRunner` without the starter (more boilerplate) — rejected.

### Bootstrap + exit-code propagation (canonical pattern)
Spring Boot apps don't exit non-zero by default; wire it explicitly so `0`/`1`/`2` still reach the shell:

```java
@SpringBootApplication
public class AregressApplication implements CommandLineRunner, ExitCodeGenerator {
    private final ReplayCommand command;   // the @Command bean (former Main)
    private final IFactory factory;        // PicocliSpringFactory from the starter
    private int exitCode;

    AregressApplication(ReplayCommand command, IFactory factory) {
        this.command = command; this.factory = factory;
    }
    @Override public void run(String... args) { exitCode = new CommandLine(command, factory).execute(args); }
    @Override public int getExitCode() { return exitCode; }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AregressApplication.class, args)));
    }
}
```

`ReplayCommand` is the existing `Main` logic moved into a `@Command @Component implements Callable<Integer>` bean (returns 0/1/2 exactly as now). Picocli's interactive password prompt continues to work under this runner.

### Externalized config + precedence
A `@ConfigurationProperties(prefix = "aregress")` record/bean holds non-secret settings — `appA`, `appB`, `cfct` (base URLs), `cfctUsername`, timeouts — injected into `ReplayCommand`. Defaults ship in a bundled `application.yml`. Precedence (highest first): **CLI option → system property → environment variable → `application.yml` → built-in default** (Spring's normal `PropertySource` order; CLI sits on top).

Resolution mechanism: option fields default to `null` (no Picocli `defaultValue`); after parsing, the command computes the effective value as `option != null ? option : props.getX()`. This keeps "CLI wins, else config" explicit. (Secrets — `--password`, `--cfct-password` — remain CLI/env only, never bound from `application.yml`.)

### Packaging
`spring-boot-maven-plugin` `repackage` produces the executable jar (main class = `AregressApplication`); drop `maven-shade-plugin` (and its `dependency-reduced-pom.xml`, already gitignored). Keep the artifact path the launcher expects, or update the launcher to the new jar name.

### Quiet startup
For clean CLI output: `spring.main.banner-mode=off` and a low-noise logging level (e.g. root `WARN`) in `application.yml`, so only the tool's own `System.out` lines appear.

## Risks / Trade-offs

- **Java baseline** — Spring Boot 4.x may require Java 17 or 21. → Confirm; set `pom` `maven.compiler.*` and the `aregress` launcher's version gate accordingly (bump from 17 if required).
- **Exit codes lost** — Boot swallows them by default. → The `ExitCodeGenerator` + `SpringApplication.exit` pattern above preserves `0`/`1`/`2`; verify with a usage error (Picocli returns `2`) and a divergence (`1`).
- **Noisy stdout** — Boot banner/logs could pollute the `[step N]` output. → banner off + quiet logging.
- **Heavier startup / larger jar** — accepted; it's a CLI run, not latency-sensitive.
- **Interactive password prompt** under the Spring runner → verify `--password` (no value) still prompts correctly.
- **Playwright under Spring** — it's a plain library; no integration concerns expected.

## Migration Plan

1. `pom.xml`: `spring-boot-starter-parent` 4.x, `spring-boot-starter`, `picocli-spring-boot-starter`, `spring-boot-maven-plugin`; remove `maven-shade-plugin`; set Java baseline.
2. Rename/move `Main` → `ReplayCommand` (`@Command @Component`, unchanged `call()`); add `AregressApplication` bootstrap (above).
3. Add `AregressProperties` (`@ConfigurationProperties`) + `application.yml` with defaults; inject into `ReplayCommand`; resolve "CLI-or-config" per option.
4. `mvn package`; verify `--help`, the timestamp/file group, exit codes, and a real run (replay/compare/import) behave exactly as before.
5. Update README (config section) and the launcher if the jar name changes.
No rollback concern beyond reverting the commit; behaviour is preserved.

## Open Questions

- Spring Boot 4.x exact version and its Java baseline (17 vs 21)?
- Which keys to externalize first — URLs, timeouts, `cfct-username` proposed; confirm. Credentials stay out of `application.yml`.
- Keep the produced jar name (`aregress-1.0-SNAPSHOT.jar`) so the launcher is untouched, or rename?
