.PHONY: build build-release clean test lint coverage check debug install release

# help, devtools-update, coverage-tooling and devtools-check come from jmerhar/devtools; make
# fetches them on the first run and whenever devtools.toml changes.
.DEFAULT_GOAL := help
include .devtools/make/devtools.mk
.devtools/make/devtools.mk: devtools.toml bin/devtools-fetch.sh
	bin/devtools-fetch.sh

##@ Build

build: ## Build debug APK
	./gradlew assembleDebug

build-release: ## Build release APK (unsigned without signing credentials)
	./gradlew assembleRelease

clean: ## Clean build and coverage outputs
	./gradlew clean
	rm -rf coverage coverage-upload junit

##@ Test

test: ## Run the unit tests and the release-script tests
	./gradlew test
	bats test/

lint: ## Run Android Lint and ShellCheck
	./gradlew lint
	shellcheck bin/*.sh test/test_helper.bash test/stubs/*

coverage: coverage-tooling ## Measure both suites and enforce the coverage gates
	./gradlew testDebugUnitTest koverHtmlReportDebug koverXmlReportDebug
	bash $(COVERAGE_TOOLING)/run-kcov.sh
	python3 $(COVERAGE_TOOLING)/coverage-report.py --format md
	python3 $(COVERAGE_TOOLING)/coverage-report.py --gate

check: lint test coverage devtools-check ## Lint, tests and coverage gates — run before pushing

##@ Device

debug: ## Build and install debug app on connected device
	./gradlew installDebug

install: ## Build and install release APK on connected device
	./gradlew installRelease

##@ Release

release: ## Bump version, build, tag, push, and publish a GitHub Release (VERSION=x.y NOTES=file [DRAFT=1])
	./bin/release.sh $(VERSION) $(if $(NOTES),-n $(NOTES)) $(if $(DRAFT),--draft)
