# Release APK Workflow

This document explains `.forgejo/workflows/release-apk.yml` for future
maintenance. The workflow builds a signed Fuel Math Android release APK when a
version tag is pushed, then publishes that APK to the Forgejo release for the
same tag.

## Trigger

The workflow runs on pushed tags matching either lowercase or uppercase version
prefixes:

```yaml
on:
  push:
    tags:
      - "v*"
      - "V*"
```

Examples that trigger the workflow:

- `v1.0.0`
- `V1.0.0`
- `v1.1.0-beta1`

Changing the tag convention only requires editing the `tags` list. Forgejo
evaluates these patterns before the job starts; a non-matching tag creates no
job.

## Runner Environment

The job uses the local Forgejo runner label:

```yaml
runs-on: ubuntu-22.04
```

It also specifies:

```yaml
container:
  image: ubuntu:22.04
  options: --network ci-network
```

The runner starts the job in an Ubuntu container and attaches it to
`ci-network`. The APT proxy setup depends on `apt-cacher-ng:3142` being
reachable from that network.

## APT Proxy and Bootstrap

The first step configures APT to use the runner-local cache:

```apt
Acquire::http::Proxy "http://apt-cacher-ng:3142";
Acquire::https::Proxy "http://apt-cacher-ng:3142";
```

It installs:

- `ca-certificates`
- `curl`
- `git`
- `gnupg`
- `jq`
- `unzip`

It also installs Node 20 from NodeSource if the base container does not already
have Node 20 or newer. JavaScript-based actions require a compatible Node
runtime inside the job container.

## External Actions

The workflow uses explicit action URLs:

```yaml
uses: https://github.com/actions/checkout@v4
uses: https://github.com/actions/setup-java@v4
uses: https://github.com/android-actions/setup-android@v3
```

Using full URLs avoids depending on self-hosted Forgejo action shorthand
rewrites. If the runner cannot reach GitHub directly, mirror these actions to an
accessible Forgejo instance and update the URLs to those exact mirror locations.

## Required Secrets

The workflow validates all required secrets before it builds:

| Secret | Purpose |
| --- | --- |
| `KEY_ALIAS` | Android signing key alias. |
| `KEY_PASSWORD` | Password for the Android signing key. |
| `KEYSTORE_BASE64` | Base64-encoded Android keystore file. |
| `KEYSTORE_PASSWORD` | Password for the Android keystore. |

Do not hardcode signing values in the workflow or Gradle files. Add the secrets
at the repository or organization scope in Forgejo.

## Build and Signing

The build command is:

```bash
./gradlew --no-daemon assembleRelease
```

The workflow expects exactly one unsigned release APK under:

```text
app/build/outputs/apk/release/*-release-unsigned.apk
```

The signing step:

1. Decodes the keystore to `${RUNNER_TEMP}/fuelmath-release.keystore`.
2. Locates the latest Android SDK build-tools directory.
3. Runs `zipalign`.
4. Runs `apksigner sign`.
5. Runs `apksigner verify`.
6. Writes the final asset to `dist/fuelmath-${TAG}.apk`.

If the Android project later gains product flavors, ABI splits, or multiple
release APKs, the signing step will fail intentionally. Update the artifact
selection and naming policy before enabling multiple release assets.

## Forgejo Release Publishing

`Publish Forgejo release` uses the Forgejo API from inside the Forgejo runner.

It derives:

- API URL from `FORGEJO_API_URL`, falling back to `GITHUB_API_URL`.
- Repository from `FORGEJO_REPOSITORY`, falling back to `GITHUB_REPOSITORY`.
- Tag from `FORGEJO_REF_NAME`, falling back to `GITHUB_REF_NAME`.
- Token from `forgejo.token`, falling back to `GITHUB_TOKEN`.

For the upstream repository at
`https://pubcode.archuser.org/android/FuelMath`, the repository context should
resolve to:

```text
android/FuelMath
```

The step:

1. Looks up the release by tag.
2. Creates it if missing.
3. Updates it if present.
4. Sets `draft: false` and `prerelease: false`.
5. Deletes any existing APK asset with the same filename.
6. Uploads the new APK asset.

The workflow does not publish to GitHub and does not migrate refs to another
remote.

## Idempotency

The release publishing step is designed to be safe to rerun for the same tag:

- existing releases are patched instead of causing failure;
- existing APK assets with the same name are deleted before upload;
- the generated APK asset name is deterministic for the tag.

This makes it safe to rerun a failed job after fixing credentials, runner
packages, or network issues.

## External Dependencies

Before finalizing CI changes, verify these references from the network where the
runner executes:

| Reference | Purpose |
| --- | --- |
| `ubuntu:22.04` | Job container image. |
| `https://github.com/actions/checkout@v4` | Repository checkout action. |
| `https://github.com/actions/setup-java@v4` | Temurin JDK setup action. |
| `https://github.com/android-actions/setup-android@v3` | Android SDK setup action. |
| `https://deb.nodesource.com/node_20.x` | Node 20 APT repository. |
| `https://services.gradle.org/distributions/gradle-8.13-bin.zip` | Gradle wrapper distribution. |
| `google()` | Android Gradle plugin and AndroidX dependencies. |
| `mavenCentral()` | JVM dependency repository. |
| `gradlePluginPortal()` | Gradle plugin repository. |
| Forgejo API URL from runner context | Release create/update and asset upload. |

If any reference is unreachable from the runner, use a verified mirror or update
the workflow to install the dependency from a reachable source.

## Common Changes

### Change the release tag pattern

Edit the `on.push.tags` list. For example, to require semantic versions only,
replace the broad patterns with exact Forgejo-supported glob patterns.

### Change the APK filename

Edit `Sign release APK` and `Publish Forgejo release` where they refer to:

```bash
dist/fuelmath-${tag}.apk
```

Keep the filename deterministic so reruns can replace the previous asset.

### Change Java or Android SDK versions

Java is controlled by:

```yaml
uses: https://github.com/actions/setup-java@v4
with:
  distribution: temurin
  java-version: "17"
```

The Android SDK setup is delegated to:

```yaml
uses: https://github.com/android-actions/setup-android@v3
```

The Android compile SDK is controlled by `compileSdk` in
`app/build.gradle.kts`.

## Troubleshooting

### Workflow did not start

Check the pushed tag. It must match `v*` or `V*`.

### Action clone failed

Verify that the fully qualified action URL is reachable from the runner. If the
runner cannot access GitHub, use a verified Forgejo mirror and update the action
URL explicitly.

### APT cannot connect to package repositories

Verify:

- the job is on `ci-network`;
- `apt-cacher-ng:3142` is reachable from that network;
- the APT proxy file is written before `apt-get update`;
- NodeSource is reachable through the configured proxy.

### Signing fails

Verify:

- all four signing secrets exist in this Forgejo repository or its owner;
- `KEYSTORE_BASE64` decodes to the intended keystore;
- `KEY_ALIAS` matches an alias in that keystore;
- `KEY_PASSWORD` and `KEYSTORE_PASSWORD` are correct.

### Duplicate asset upload fails

The workflow deletes an existing asset with the same filename before uploading.
If the failure persists, check whether Forgejo renamed the previous asset or left
an incomplete asset after an earlier upload failure.
