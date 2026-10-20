#!/usr/bin/env bash
# Shared setup for the bats suite: a throwaway git repository shaped like this one, with a
# bare remote, a stub ./gradlew, a stub apksigner and a stubbed `gh` on PATH, so
# bin/release.sh runs unmodified without building, signing or reaching GitHub.

setup_common() {
  REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
  export REPO_ROOT

  # Stub behaviour is driven by files here; calls the stubs receive are appended to CALLS.
  STUB_STATE="$BATS_TEST_TMPDIR/stub-state"
  CALLS="$BATS_TEST_TMPDIR/calls"
  export STUB_STATE CALLS
  mkdir -p "$STUB_STATE"
  : > "$CALLS"

  PATH="$BATS_TEST_DIRNAME/stubs:$PATH"
  export PATH

  # Keep the developer's git identity and config out of the fixture.
  export GIT_CONFIG_GLOBAL="$BATS_TEST_TMPDIR/gitconfig"
  export GIT_CONFIG_NOSYSTEM=1
  git config --global user.name "Test"
  git config --global user.email "test@example.com"
  git config --global init.defaultBranch main
  git config --global advice.detachedHead false

  # apksigner is found under $ANDROID_HOME/build-tools/<version>/.
  export ANDROID_HOME="$BATS_TEST_TMPDIR/android-sdk"
  mkdir -p "$ANDROID_HOME/build-tools/35.0.0"
  cp "$BATS_TEST_DIRNAME/stubs/_apksigner" "$ANDROID_HOME/build-tools/35.0.0/apksigner"

  unset ROAMER_KEYSTORE_PASSWORD

  make_fixture_repo
}

# Builds $FIXTURE: a repository on main, in sync with a bare origin, ready to release.
make_fixture_repo() {
  ORIGIN="$BATS_TEST_TMPDIR/origin.git"
  FIXTURE="$BATS_TEST_TMPDIR/repo"
  export ORIGIN FIXTURE
  git init --quiet --bare "$ORIGIN"
  git init --quiet "$FIXTURE"
  cd "$FIXTURE" || return 1

  mkdir -p app keystore
  cat > app/build.gradle.kts <<'EOF'
android {
    defaultConfig {
        versionCode = 4
        versionName = "1.2"
    }
}
EOF
  cp "$BATS_TEST_DIRNAME/stubs/_gradlew" gradlew
  printf 'build/\nkeystore/\nkeystore.properties\nnotes.md\n' > .gitignore
  : > keystore/release.keystore
  printf 'storePassword=secret\n' > keystore.properties
  printf 'Fixes things.\n' > notes.md

  git add .
  git commit --quiet -m "initial"
  git remote add origin "$ORIGIN"
  git push --quiet -u origin main
}

# Makes a stub fail: `stub_fails gradle-test`, `stub_fails gradle-build`, `stub_fails apksigner`,
# `stub_fails gh-auth`, `stub_fails gh-release`. `stub_unsigned` makes the build emit only an
# unsigned APK.
stub_fails() { : > "$STUB_STATE/$1-fails"; }
stub_unsigned() { : > "$STUB_STATE/unsigned"; }

# Prints the version line currently in the fixture's build file.
version_code() { sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$FIXTURE/app/build.gradle.kts"; }
version_name() { sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$FIXTURE/app/build.gradle.kts"; }

# Asserts the release left no trace: no bump, no new commit, no tag, locally or on origin.
assert_untouched() {
  [ "$(version_code)" = 4 ] || { echo "versionCode is $(version_code)"; return 1; }
  [ -z "$(git -C "$FIXTURE" status --porcelain)" ] || { git -C "$FIXTURE" status; return 1; }
  [ "$(git -C "$FIXTURE" log --format=%s -1)" = initial ] || return 1
  [ -z "$(git -C "$FIXTURE" tag)" ] || return 1
  [ -z "$(git -C "$ORIGIN" tag)" ] || return 1
}

# Runs bin/release.sh from inside the fixture.
#
# With COVERAGE_DIR set, the script runs through kcov, which accumulates line coverage into
# that directory across every invocation in the suite. kcov must be given the *script* as its
# target — handing it `bash script` instruments the bash binary instead and reports nothing. It
# passes the exit status and output through, so assertions behave the same either way.
run_release() {
  cd "$FIXTURE" || return 1
  if [ -n "${COVERAGE_DIR:-}" ]; then
    run kcov --include-path="$REPO_ROOT/bin" "$COVERAGE_DIR" "$REPO_ROOT/bin/release.sh" "$@"
  else
    run bash "$REPO_ROOT/bin/release.sh" "$@"
  fi
}
