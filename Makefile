.PHONY: build build-release clean test lint coverage coverage-tooling check debug install release help

## Build

build: ## Build debug APK
	./gradlew assembleDebug

build-release: ## Build release APK (unsigned without signing credentials)
	./gradlew assembleRelease

clean: ## Clean build and coverage outputs
	./gradlew clean
	rm -rf coverage coverage-upload junit $(COVERAGE_REPORT)

## Test

test: ## Run the unit tests and the release-script tests
	./gradlew test
	bats test/

lint: ## Run Android Lint and ShellCheck
	./gradlew lint
	shellcheck bin/*.sh test/test_helper.bash test/stubs/*

# The coverage summary and gate are shared tooling from jmerhar/coverage, configured by
# coverage.toml. Fetched rather than vendored, so a local gate enforces exactly what CI does.
# Refreshed on every run because v1 moves within its major version; -z makes an unchanged file
# cost a 304, and a failed request falls back to the copy on disk, so this works offline.
COVERAGE_REPORT := .coverage-report.py

coverage-tooling:
	@curl -fsSL -z $(COVERAGE_REPORT) -o $(COVERAGE_REPORT) \
		https://raw.githubusercontent.com/jmerhar/coverage/v1/bin/coverage-report.py \
		|| test -f $(COVERAGE_REPORT)

coverage: coverage-tooling ## Measure both suites and enforce the coverage gates
	./gradlew testDebugUnitTest koverHtmlReportDebug koverXmlReportDebug
	bin/run-shell-coverage.sh
	python3 $(COVERAGE_REPORT) --format md
	python3 $(COVERAGE_REPORT) --gate

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
