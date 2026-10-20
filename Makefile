.PHONY: build build-release clean test lint coverage coverage-tooling check debug install release help

## Build

build: ## Build debug APK
	./gradlew assembleDebug

build-release: ## Build release APK (unsigned without signing credentials)
	./gradlew assembleRelease

clean: ## Clean build and coverage outputs
	./gradlew clean
	rm -rf coverage coverage-upload junit $(COVERAGE_TOOLING)

## Test

test: ## Run the unit tests and the release-script tests
	./gradlew test
	bats test/

lint: ## Run Android Lint and ShellCheck
	./gradlew lint
	shellcheck bin/*.sh test/test_helper.bash test/stubs/*

# The kcov runner, the summary and the gate are shared tooling from jmerhar/coverage, configured
# by coverage.toml. Fetched rather than vendored, so a local run does exactly what CI does, and
# kept side by side because run-kcov.sh finds coverage-report.py in its own directory. Refreshed on
# every run because v1 moves within its major version; -z makes an unchanged file cost a 304, and a
# failed request falls back to the copy on disk, so this works offline.
COVERAGE_TOOLING := .coverage-tooling

coverage-tooling:
	@mkdir -p $(COVERAGE_TOOLING)
	@for f in coverage-report.py run-kcov.sh; do \
		curl -fsSL -z $(COVERAGE_TOOLING)/$$f -o $(COVERAGE_TOOLING)/$$f \
			https://raw.githubusercontent.com/jmerhar/coverage/v1/bin/$$f \
			|| test -f $(COVERAGE_TOOLING)/$$f || exit 1; \
	done

coverage: coverage-tooling ## Measure both suites and enforce the coverage gates
	./gradlew testDebugUnitTest koverHtmlReportDebug koverXmlReportDebug
	bash $(COVERAGE_TOOLING)/run-kcov.sh
	python3 $(COVERAGE_TOOLING)/coverage-report.py --format md
	python3 $(COVERAGE_TOOLING)/coverage-report.py --gate

check: lint test coverage ## Lint, tests and coverage gates — run before pushing

## Device

debug: ## Build and install debug app on connected device
	./gradlew installDebug

install: ## Build and install release APK on connected device
	./gradlew installRelease

## Release

release: ## Bump version, build, tag, push, and publish a GitHub Release (VERSION=x.y NOTES=file [DRAFT=1])
	./bin/release.sh $(VERSION) $(if $(NOTES),-n $(NOTES)) $(if $(DRAFT),--draft)

## Help

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

.DEFAULT_GOAL := help
