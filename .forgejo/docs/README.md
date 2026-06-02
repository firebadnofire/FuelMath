# Fuel Math Forgejo APK Release Workflow

Use this checklist when maintaining this repository's `.forgejo/` APK release
workflow. The detailed workflow behavior is documented in
[`release-apk-workflow.md`](release-apk-workflow.md).

## Plan

1. Keep `.forgejo/workflows/release-apk.yml` aligned with the Fuel Math Android
   package, artifact names, and Forgejo repository.
2. Add the required Forgejo secrets for Android release signing.
3. Validate the workflow syntax and local release build before pushing a tag.

## Implementation

### Project Names

The Fuel Math workflow uses these project-specific values:

```bash
keystore_path="${temp_dir}/fuelmath-release.keystore"
aligned_apk="dist/fuelmath-${tag}-aligned.apk"
signed_apk="dist/fuelmath-${tag}.apk"
asset_path="dist/fuelmath-${tag}.apk"
release_name="Fuel Math ${tag}"
```

The Forgejo owner and repository are not hardcoded. The workflow derives them
from `FORGEJO_REPOSITORY`, falling back to `GITHUB_REPOSITORY` for runner
compatibility. For the upstream repository at
`https://pubcode.archuser.org/android/FuelMath`, that resolves to
`android/FuelMath`.

### Release Signing

Add these secrets in Forgejo for the target repository or owning organization:

```text
KEY_ALIAS
KEY_PASSWORD
KEYSTORE_BASE64
KEYSTORE_PASSWORD
```

Generate `KEYSTORE_BASE64` from the keystore file with:

```bash
base64 -i release.keystore
```

Use the output as the secret value. Do not commit the keystore, signing
passwords, or token values.

The workflow builds the unsigned release APK with:

```bash
./gradlew --no-daemon assembleRelease
```

Then it uses Android SDK build-tools to run `zipalign`, `apksigner sign`, and
`apksigner verify`. Passwords are passed to `apksigner` through environment
variables, not command-line literal values.

### External References

The workflow intentionally uses fully qualified action references:

```yaml
uses: https://github.com/actions/checkout@v4
uses: https://github.com/actions/setup-java@v4
uses: https://github.com/android-actions/setup-android@v3
```

This avoids relying on self-hosted Forgejo shorthand rewrites such as
`actions/checkout`.

The job also downloads packages from Ubuntu APT, NodeSource, Gradle, Google
Maven, Maven Central, and the Gradle Plugin Portal. The runner must have
outbound network access to those sources, or must provide working mirrors through
the configured `apt-cacher-ng` proxy and normal Gradle dependency resolution.

## Validation

Run these checks in the repository before pushing a release tag:

```bash
ruby -e 'require "yaml"; YAML.load_file(".forgejo/workflows/release-apk.yml"); puts "yaml ok"'
rg -n 'Launch ''Pad|launch''pad|Launch''Pad|firebad''nofire|GH_''KEY|GitHub ''release|Migrate ''repository|uploads\.''github|api\.''github|github\.com/firebad''nofire' .forgejo
./gradlew --no-daemon tasks --all
./gradlew --no-daemon assembleRelease
```

The local `assembleRelease` command produces an unsigned APK unless release
signing is configured locally. That is acceptable for local validation. The CI
job signs the APK after Gradle builds it.

After validation, push a version tag:

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```
