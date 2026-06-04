# Fuel Math Release Workflow Notes

Use this checklist when maintaining or adapting Fuel Math's Forgejo release automation.
The detailed workflow behavior is documented in
[`release-apk-workflow.md`](release-apk-workflow.md).

## Plan

1. Confirm the workflow still matches Fuel Math's release naming and signing inputs.
2. Keep Forgejo publishing required and GitHub publishing optional.
3. Confirm the app can read release signing values from environment variables.
4. Add or update the required Forgejo secrets and optional GitHub variables.
5. Validate the workflow syntax and the local release build before pushing a tag.

## Implementation

### Keep Project Naming Consistent

Edit `.forgejo/workflows/release-apk.yml`:

```bash
keystore_path="${temp_dir}/fuelmath-release.keystore"
export RELEASE_KEYSTORE_PATH="${temp_dir}/fuelmath-release.keystore"
cp "${apk_files[0]}" "dist/fuelmath-${tag}.apk"
asset_path="dist/fuelmath-${tag}.apk"
release_name="Fuel Math ${tag}"
```

If Fuel Math ever publishes to GitHub, set repository variables instead of
hard-coding a second host target into the workflow:

```text
GITHUB_RELEASE_OWNER
GITHUB_RELEASE_REPO
```

### Remove Source-Project Leftovers

Search for assumptions from the source project:

```bash
rg -n "trapmaster|simplewallet|upstream|PWA|PROJECT_SLUG|APP_NAME|GITHUB_REPO_NAME|milestones" .forgejo
```

Remove any source-app setup steps that the target app does not need. For a normal
native Android app, there should not be a step that fetches another app or web
asset before Gradle runs.

### Wire Release Signing in Gradle

The workflow exports these values before `assembleRelease`:

```text
RELEASE_KEYSTORE_PATH
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

The Android app must use them in its release signing config. If the target app
does not already do that, add a conditional signing config to the module
`build.gradle.kts`:

```kotlin
val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

signingConfigs {
    if (hasReleaseSigning) {
        create("release") {
            storeFile = file(releaseKeystorePath!!)
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
}

buildTypes {
    release {
        if (hasReleaseSigning) {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Keep the app's existing `isMinifyEnabled`, `proguardFiles`, and other release
settings. Do not hardcode passwords, aliases, tokens, or keystore paths.

### Configure Secrets

Add these required secrets in Forgejo for Fuel Math:

```text
KEY_ALIAS
KEY_PASSWORD
KEYSTORE_BASE64
KEYSTORE_PASSWORD
```

Optional GitHub mirroring needs:

```text
GH_KEY
GITHUB_RELEASE_OWNER
GITHUB_RELEASE_REPO
```

`GH_KEY` must be able to create and edit releases and upload release assets in
the configured GitHub target repository. A fine-grained GitHub token should have
repository `Contents: Read and write`.

Generate `KEYSTORE_BASE64` from the keystore file with:

```bash
base64 -i release.keystore
```

Use the output as the secret value. Do not commit the keystore.

## Validation

Run these checks in the target repository before pushing a release tag:

```bash
ruby -e 'require "yaml"; YAML.load_file(".forgejo/workflows/release-apk.yml"); puts "yaml ok"'
rg -n "trapmaster|simplewallet|upstream|PWA|PROJECT_SLUG|APP_NAME|GITHUB_REPO_NAME" .forgejo
rg -n "milestones" .forgejo
GRADLE_USER_HOME="$PWD/.gradle" sh ./gradlew --no-daemon tasks --all
GRADLE_USER_HOME="$PWD/.gradle" sh ./gradlew --no-daemon assembleRelease
```

The local `assembleRelease` may produce an unsigned APK if signing secrets are not
present. That is acceptable for local validation. The CI job should produce a
signed APK when the secrets are configured.

After validation, push a version tag:

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

If optional GitHub release publishing fails with a 404, check the
`GITHUB_RELEASE_OWNER` and `GITHUB_RELEASE_REPO` values first, then verify that
`GH_KEY` has access to that repository.
