# Release APK Workflow

This document explains `.forgejo/workflows/release-apk.yml` for future maintenance.
The workflow builds a signed Android release APK when a version tag is pushed, then
publishes that APK to Forgejo releases and, when configured, to a GitHub mirror.

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

- `v1.3.6`
- `V1.3.6`
- `v2.0.0-beta1`

Changing the tag convention only requires editing the `tags` list. Remember that
Forgejo evaluates these patterns before the job starts; a tag that does not match
will not create a skipped job, it will create no job at all.

## Runner Environment

The job uses the local Forgejo runner label:

```yaml
runs-on: ubuntu-22.04
```

On this runner, that label maps to:

```text
ubuntu-22.04:docker://ubuntu:22.04
```

The job also specifies:

```yaml
container:
  image: ubuntu:22.04
  options: --network ci-network
```

This is not Docker-in-Docker. The runner starts the job in an Ubuntu container on
the host Docker daemon and attaches it to the runner-provided `ci-network`.

Do not add Docker service containers, Docker socket mounts, or DinD setup unless
the runner configuration changes.

## APT Proxy and Bootstrap

The first step configures APT to use the runner-local cache:

```apt
Acquire::http::Proxy "http://apt-cacher-ng:3142";
Acquire::https::Proxy "http://apt-cacher-ng:3142";
```

This depends on the job container being on `ci-network`, where
`apt-cacher-ng:3142` is reachable.

The bootstrap step installs:

- `ca-certificates`
- `curl`
- `git`
- `gnupg`
- `jq`
- `unzip`

It also installs Node 20 from NodeSource if the base container does not already
have Node 20 or newer. This is necessary because `actions/checkout@v4`,
`actions/setup-java@v4`, and most JavaScript-based actions require a Node runtime
inside the job container.

If the runner image changes to an image that already includes these tools and
Node 20, the bootstrap step can be simplified, but keep the APT proxy setup before
any `apt-get update`.

## External Actions

The workflow uses three actions:

```yaml
uses: actions/checkout@v4
uses: actions/setup-java@v4
uses: https://github.com/android-actions/setup-android@v3
```

The first two resolve through Forgejo's action mirror on this runner.

The Android SDK action must use the full GitHub URL because Forgejo otherwise
tries to resolve `android-actions/setup-android` through `data.forgejo.org`, where
that repository is not available.

If another action fails with a `data.forgejo.org/... not found` error, either:

- use a full URL such as `https://github.com/owner/action@tag`, or
- replace the action with an explicit shell step.

## Required Secrets

The workflow validates all required secrets before it builds:

| Secret | Purpose |
| --- | --- |
| `KEY_ALIAS` | Android signing key alias. |
| `KEY_PASSWORD` | Password for the Android signing key. |
| `KEYSTORE_BASE64` | Base64-encoded Android keystore file. |
| `KEYSTORE_PASSWORD` | Password for the Android keystore. |
Optional GitHub mirroring also uses:

| Setting | Purpose |
| --- | --- |
| `GH_KEY` | GitHub access token used to publish to the configured GitHub mirror. |
| `GITHUB_RELEASE_OWNER` | GitHub owner or organization for the mirror repository. |
| `GITHUB_RELEASE_REPO` | GitHub repository name for the mirror repository. |

`GH_KEY` should have enough permission to create and edit releases and upload
release assets for the configured GitHub repository. For a fine-grained GitHub
token, use repository `Contents: Read and write`.

Secret scope matters. `GH_KEY` must be available to this repository's workflows.
Add it in one of these places:

- `/{owner}/{repo}/settings/actions/secrets` for this repository only.
- `/org/{org}/settings/actions/secrets` if the owner is an organization and the
  token should be available to all repositories in that organization.
- `/user/settings/actions/secrets` only works for repositories that belong to that
  user account.

Do not hardcode any signing values or tokens in the workflow.

## Android Signing

`Decode release keystore` writes the secret keystore to a temporary file:

```bash
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$keystore_path"
chmod 600 "$keystore_path"
```

`Build release APK` exports `RELEASE_KEYSTORE_PATH` before running Gradle.

The Gradle build reads these environment variables:

- `RELEASE_KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

When all values are present and the keystore file exists, the `release` build type
uses the release signing config. Local release builds without these variables keep
Gradle's normal unsigned release behavior.

If install upgrades start failing on Android devices, verify that the same signing
keystore is being used across releases.

## Build Output

The build command is:

```bash
./gradlew --no-daemon assembleRelease
```

The workflow expects exactly one APK under:

```text
app/build/outputs/apk/release/*.apk
```

It copies that APK to:

```text
dist/fuelmath-${TAG}.apk
```

If the Android project later gains flavors, ABI splits, or multiple release APKs,
this step will fail intentionally. Update the asset selection and naming policy
before enabling multiple release artifacts.

## Forgejo Release Publishing

`Publish Forgejo release` uses the Forgejo API from inside the Forgejo runner.

It derives:

- API URL from `FORGEJO_API_URL`, falling back to `GITHUB_API_URL`
- repository from `FORGEJO_REPOSITORY`, falling back to `GITHUB_REPOSITORY`
- tag from `FORGEJO_REF_NAME`, falling back to `GITHUB_REF_NAME`
- token from `forgejo.token`, falling back to `GITHUB_TOKEN`

The step:

1. Looks up the release by tag.
2. Creates it if missing.
3. Updates it if present.
4. Sets `draft: false` and `prerelease: false`.
5. Deletes any existing APK asset with the same filename.
6. Uploads the new APK asset.

Forgejo does not expose a `make_latest` flag on this host. Its latest release is
determined by normal release semantics: non-draft, non-prerelease releases are
eligible for latest.

## GitHub Release Publishing

`Publish GitHub release` uses `GH_KEY` and GitHub's REST API directly when
`GH_KEY`, `GITHUB_RELEASE_OWNER`, and `GITHUB_RELEASE_REPO` are all set. It does
not run GitHub Actions.

If any of those values are missing, the workflow prints a skip message and leaves
GitHub publishing disabled.

The step:

1. Looks up the GitHub release by the same tag.
2. Creates it if missing.
3. Updates it if present.
4. Sets `draft: false`, `prerelease: false`, and `make_latest: "true"`.
5. Deletes any existing APK asset with the same filename.
6. Uploads the APK to `uploads.github.com`.

GitHub release assets use a different upload host than the normal API:

```text
https://uploads.github.com/repos/{owner}/{repo}/releases/{release_id}/assets
```

If GitHub release creation fails because the tag does not exist on GitHub, verify
that the tag was pushed or mirrored to the configured GitHub repository. The workflow does
not pass Forgejo's commit SHA as `target_commitish`, because that SHA may not
exist in GitHub. If the tag is missing on GitHub, GitHub creates it from the
target repository's default branch when the release is created.

## Idempotency

The release publishing steps are designed to be safe to rerun for the same tag:

- existing releases are patched instead of causing failure;
- existing APK assets with the same name are deleted before upload;
- the generated APK asset name is deterministic for the tag.

This makes it safe to rerun a failed job after fixing credentials, runner
packages, or network issues.

## Common Changes

### Change the release tag pattern

Edit the `on.push.tags` list. For example, to require semantic versions only,
replace the broad patterns with the exact Forgejo-supported glob patterns you
want to allow.

### Change the APK filename

Edit the `Prepare release asset` step and both publish steps that refer to:

```bash
asset_path="dist/fuelmath-${tag}.apk"
```

Keep the filename deterministic so reruns can replace the previous asset.

### Publish only to Forgejo

Leave `GH_KEY`, `GITHUB_RELEASE_OWNER`, and `GITHUB_RELEASE_REPO` unset.

### Publish only to GitHub

Remove the `Publish Forgejo release` step. Keep GitHub validation.

### Change the GitHub target repository

Set these repository variables and the matching secret:

```text
GH_KEY
GITHUB_RELEASE_OWNER
GITHUB_RELEASE_REPO
```

Make sure `GH_KEY` has permission for the new target repository.

### Change Java or Android SDK versions

Java is controlled by:

```yaml
uses: actions/setup-java@v4
with:
  distribution: temurin
  java-version: "17"
```

Android SDK setup is currently delegated to
`https://github.com/android-actions/setup-android@v3`. The exact Android compile
SDK is controlled by `compileSdk` in `app/build.gradle.kts`.

## Troubleshooting

### Workflow did not start

Check the pushed tag. It must match `v*` or `V*`.

### Action clone failed from `data.forgejo.org`

Use a full action URL, for example:

```yaml
uses: https://github.com/android-actions/setup-android@v3
```

### APT cannot connect to package repositories

Verify:

- the job is on `ci-network`;
- `apt-cacher-ng:3142` is reachable from that network;
- the APT proxy file is written before `apt-get update`.

### GitHub upload fails with duplicate asset

The workflow should delete an asset with the same name before uploading. If the
failure persists, check whether GitHub renamed the previous asset or left a failed
`starter` asset after an upstream error.

### GitHub release is not latest

GitHub supports `make_latest: "true"` and the workflow sets it on create/update.
Drafts and prereleases cannot be latest, so also verify that the release is not a
draft and not marked as a prerelease.
