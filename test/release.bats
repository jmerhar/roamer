#!/usr/bin/env bats
# Tests for bin/release.sh, run against a throwaway repository with a bare origin (see
# test_helper.bash), so nothing is built, signed or published.

load test_helper

setup() { setup_common; }

# --- Arguments ---

@test "requires a version" {
  run_release
  [ "$status" -ne 0 ]
  [[ "$output" == *"Usage:"* ]]
  assert_untouched
}

@test "rejects a version that is not major.minor" {
  run_release 1.2.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Version must look like 1.2 (got '1.2.3')"* ]]
  assert_untouched
}

@test "rejects an unknown option" {
  run_release 1.3 -n notes.md --force
  [ "$status" -eq 1 ]
  [[ "$output" == *"Unknown option: --force"* ]]
}

@test "-n requires a value" {
  run_release 1.3 -n
  [ "$status" -ne 0 ]
  [[ "$output" == *"-n requires a file path"* ]]
}

# --- Pre-flight ---

@test "refuses to release from a branch other than main" {
  git checkout --quiet -b feature
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Must be on the main branch"*"'feature'"* ]]
}

@test "refuses with unstaged changes" {
  echo "// edit" >> app/build.gradle.kts
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"uncommitted changes"* ]]
}

@test "refuses with staged changes" {
  echo "edit" > staged.txt
  git add staged.txt
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"uncommitted changes"* ]]
}

@test "refuses when local main is ahead of origin" {
  git commit --quiet --allow-empty -m "unpushed"
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Local main does not match origin/main"* ]]
  [ -z "$(git tag)" ]
}

@test "refuses when origin has commits local main lacks" {
  other="$BATS_TEST_TMPDIR/other"
  git clone --quiet "$ORIGIN" "$other"
  git -C "$other" commit --quiet --allow-empty -m "pushed elsewhere"
  git -C "$other" push --quiet origin main
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Local main does not match origin/main"* ]]
  assert_untouched
}

@test "refuses when origin cannot be fetched" {
  git remote set-url origin "$BATS_TEST_TMPDIR/missing.git"
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Could not fetch origin/main"* ]]
}

@test "requires release notes" {
  run_release 1.3
  [ "$status" -eq 1 ]
  [[ "$output" == *"Release notes are required"* ]]
}

@test "refuses a notes file that does not exist" {
  run_release 1.3 -n nope.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Notes file not found: nope.md"* ]]
}

@test "refuses an empty notes file" {
  : > notes.md
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Notes file is empty"* ]]
}

@test "refuses a tag that already exists" {
  git tag v1.3
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Tag v1.3 already exists"* ]]
}

@test "refuses when gh is not authenticated" {
  stub_fails gh-auth
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"gh is not authenticated"* ]]
}

@test "refuses without a keystore" {
  rm keystore/release.keystore
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Signing keystore not found"* ]]
}

@test "refuses without signing credentials" {
  rm keystore.properties
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"No signing credentials"* ]]
}

@test "accepts signing credentials from the environment" {
  rm keystore.properties
  ROAMER_KEYSTORE_PASSWORD=secret run_release 1.3 -n notes.md
  [ "$status" -eq 0 ]
}

@test "refuses when the tests fail, before bumping anything" {
  stub_fails gradle-test
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Tests failed"* ]]
  ! grep -q assembleRelease "$CALLS"
  assert_untouched
}

@test "refuses a build file without exactly one versionCode" {
  printf 'versionCode = 9\n' >> app/build.gradle.kts
  git commit --quiet -am "second versionCode"
  git push --quiet
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Expected exactly one versionCode"* ]]
}

# --- Build failures roll the bump back ---

@test "rolls the version back when the build fails" {
  stub_fails gradle-build
  run_release 1.3 -n notes.md
  [ "$status" -ne 0 ]
  assert_untouched
}

@test "rolls the version back when the build emits an unsigned APK" {
  stub_unsigned
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Release APK not found"* ]]
  assert_untouched
}

@test "rolls the version back when the APK signature does not verify" {
  stub_fails apksigner
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"not correctly signed"* ]]
  assert_untouched
}

@test "warns and continues when apksigner is not installed" {
  rm -r "$ANDROID_HOME/build-tools"
  run_release 1.3 -n notes.md
  [ "$status" -eq 0 ]
  [[ "$output" == *"WARNING: apksigner not found"* ]]
}

# --- Publishing ---

@test "bumps, commits, tags, pushes and creates the release" {
  run_release 1.3 -n notes.md
  [ "$status" -eq 0 ]

  [ "$(version_code)" = 5 ]
  [ "$(version_name)" = 1.3 ]
  [ "$(git log --format=%s -1)" = "chore: release v1.3" ]
  [ -z "$(git status --porcelain)" ]
  [ "$(git -C "$ORIGIN" rev-parse main)" = "$(git rev-parse HEAD)" ]
  [ "$(git -C "$ORIGIN" rev-parse v1.3^{commit})" = "$(git rev-parse HEAD)" ]
  [ "$(git cat-file -t v1.3)" = tag ]
  [ -f app/build/outputs/apk/release/roamer-1.3.apk ]

  grep -q "apksigner verify app/build/outputs/apk/release/roamer-release.apk" "$CALLS"
  release_call=$(grep '^gh release create' "$CALLS")
  [[ "$release_call" == *"v1.3 app/build/outputs/apk/release/roamer-1.3.apk --title Roamer 1.3"* ]]
  [[ "$release_call" == *"Fixes things."* ]]
  [[ "$release_call" != *"--draft"* ]]
}

@test "the first release has no changelog link" {
  run_release 1.3 -n notes.md
  [ "$status" -eq 0 ]
  ! grep -q "Full Changelog" "$CALLS"
}

@test "links the changelog from the previous tag" {
  git tag -a v1.2 -m "Release 1.2"
  git push --quiet origin v1.2
  run_release 1.3 -n notes.md
  [ "$status" -eq 0 ]
  grep -q "Full Changelog\*\*: https://github.com/jmerhar/roamer/compare/v1.2...v1.3" "$CALLS"
}

@test "--draft creates a draft release" {
  run_release 1.3 -n notes.md --draft
  [ "$status" -eq 0 ]
  grep '^gh release create' "$CALLS" | grep -q -- "--draft"
}

@test "explains how to finish when the release cannot be created" {
  stub_fails gh-release
  run_release 1.3 -n notes.md
  [ "$status" -eq 1 ]
  [[ "$output" == *"Tag v1.3 is pushed but the GitHub Release was not created"* ]]
  [[ "$output" == *"gh release create v1.3"* ]]
  # The bump is committed and public by then, so it is kept rather than rolled back.
  [ "$(version_code)" = 5 ]
  [ -n "$(git -C "$ORIGIN" tag)" ]
}
