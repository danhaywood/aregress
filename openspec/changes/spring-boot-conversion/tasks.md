## 1. Build

- [ ] 1.1 Confirm the Spring Boot 4.x version and its Java baseline (17 vs 21); set `pom.xml` `maven.compiler.*` and the `aregress` launcher's version gate accordingly.
- [ ] 1.2 Adopt `spring-boot-starter-parent` 4.x; add `spring-boot-starter` (core, no web), `picocli-spring-boot-starter`; keep Playwright + Gson; remove the `picocli` direct dependency if the starter supplies it.
- [ ] 1.3 Replace `maven-shade-plugin` with `spring-boot-maven-plugin` (`repackage`); main class = `AregressApplication`.

## 2. Bootstrap

- [ ] 2.1 Move `Main`'s command logic into `ReplayCommand` — `@Command @Component implements Callable<Integer>`, returning `0`/`1`/`2` exactly as now (replay/compare/import unchanged).
- [ ] 2.2 Add `AregressApplication` (`@SpringBootApplication implements CommandLineRunner, ExitCodeGenerator`): run the Picocli command via the Spring `IFactory`, capture the exit code, and `System.exit(SpringApplication.exit(...))` so `0`/`1`/`2` reach the shell.
- [ ] 2.3 Quiet startup for clean CLI output: `spring.main.banner-mode=off` and a low-noise logging level in `application.yml`.

## 3. Externalized configuration

- [ ] 3.1 Add `AregressProperties` (`@ConfigurationProperties(prefix = "aregress")`) for app-a/app-b/cfct base URLs, cfct username, and timeouts; ship defaults in `application.yml`.
- [ ] 3.2 Inject `AregressProperties` into `ReplayCommand`; make the corresponding option fields default to `null` and resolve the effective value as `option != null ? option : props.getX()` (CLI overrides config). Keep secrets (`--password`, `--cfct-password`) CLI/prompt-only — not bound from config.

## 4. Verify

- [ ] 4.1 `mvn package`; verify the executable jar runs via `java -jar` and the `aregress` launcher.
- [ ] 4.2 Verify behaviour is preserved: `--help`, the `--timestamp`/`--file` group, usage errors exit `2`, interactive `--password` still prompts, and `[step N]` output is clean (no banner/log noise).
- [ ] 4.3 Verify config precedence: a default run uses bundled defaults; an env/system-property override is honoured; a CLI option overrides config.
- [ ] 4.4 Smoke-test a real run (replay/compare, or `--file`) to confirm end-to-end behaviour and exit codes are unchanged.

## 5. Docs

- [ ] 5.1 Update README: Spring Boot build/run, the `aregress.*` config keys + `application.yml`, and the config precedence (CLI > env/property > application.yml > default); update the launcher note if the jar name changed.
