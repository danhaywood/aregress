## 1. Build

- [x] 1.1 Confirm the Spring Boot 4.x version and its Java baseline (17 vs 21); set `pom.xml` `maven.compiler.*` and the `aregress` launcher's version gate accordingly.
- [x] 1.2 Adopt `spring-boot-starter-parent` 4.x; add `spring-boot-starter` (core, no web), `picocli-spring-boot-starter`; keep Playwright + Gson; remove the `picocli` direct dependency if the starter supplies it.
- [x] 1.3 Replace `maven-shade-plugin` with `spring-boot-maven-plugin` (`repackage`); main class = `AregressApplication`.

## 2. Bootstrap

- [x] 2.1 Move `Main`'s command logic into `ReplayCommand` — `@Command @Component implements Callable<Integer>`, returning `0`/`1`/`2` exactly as now (replay/compare/import unchanged).
- [x] 2.2 Add `AregressApplication` (`@SpringBootApplication implements CommandLineRunner, ExitCodeGenerator`): run the Picocli command via the Spring `IFactory`, capture the exit code, and `System.exit(SpringApplication.exit(...))` so `0`/`1`/`2` reach the shell.
- [x] 2.3 Quiet startup for clean CLI output: `spring.main.banner-mode=off` and a low-noise logging level in `application.yml`.

## 3. Externalized configuration

- [x] 3.1 Add `AregressProperties` (`@ConfigurationProperties(prefix = "aregress")`) for app-a/app-b/cfct base URLs, cfct username, and timeouts; ship defaults in `application.yml`.
- [x] 3.2 Inject `AregressProperties` into `ReplayCommand`; make the corresponding option fields default to `null` and resolve the effective value as `option != null ? option : props.getX()` (CLI overrides config). Keep secrets (`--password`, `--cfct-password`) CLI/prompt-only — not bound from config.

## 4. Verify

- [x] 4.1 `mvn package`; verify the executable jar runs via `java -jar` and the `aregress` launcher.
- [x] 4.2 Verify behaviour is preserved: `--help`, the `--timestamp`/`--file` group, usage errors exit `2`, interactive `--password` still prompts, and `[step N]` output is clean (no banner/log noise).
- [x] 4.3 Verified config precedence on live runs: `--cfct http://localhost:19999` won over config (error named 19999); `AREGRESS_CFCT=...19998` env var won when no --cfct (error named 19998); app-a/app-b came from config defaults. CLI > env/property > application.yml > default.
- [x] 4.4 Smoke-tested a real multi-step run under Spring Boot (live cfct): clean OK steps (incl. no-footprint), real cfct REST comparisons, and the genuine updateLocale/app-b regression caught with exit 1 — behaviour and exit codes unchanged from the pre-conversion build.

## 5. Docs

- [x] 5.1 Update README: Spring Boot build/run, the `aregress.*` config keys + `application.yml`, and the config precedence (CLI > env/property > application.yml > default); update the launcher note if the jar name changed.
