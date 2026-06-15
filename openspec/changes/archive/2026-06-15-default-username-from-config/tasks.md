## 1. Configure default username

- [x] 1.1 Add a `username` property (default `estatio-admin`) with getter/setter to `AregressProperties`
- [x] 1.2 Add `aregress.username: estatio-admin` to `src/main/resources/application.yml`

## 2. Make `--username` optional with config fallback

- [x] 2.1 Remove `required = true` from the `--username` option in `ReplayCommand` and update its description to note the config fallback
- [x] 2.2 In `call()`, resolve `String user = username != null ? username : props.getUsername();` and use it everywhere the previous `username` field was used (import client + both `CausewayReplayPage` logins)
- [x] 2.3 Fail fast with a clear error and non-zero exit if the resolved username is null/blank

## 3. Documentation

- [x] 3.1 Update `README.adoc`: `--username` is now optional and defaults to `estatio-admin` (move it from the required-options section)

## 4. Verify

- [x] 4.1 `mvn package` builds cleanly
- [x] 4.2 Sanity-check `aregress.sh --help` shows `--username` as optional
