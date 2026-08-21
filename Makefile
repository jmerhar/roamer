.PHONY: build build-release clean test lint check debug install release help

## Build

build: ## Build debug APK
	./gradlew assembleDebug

build-release: ## Build release APK (unsigned without signing credentials)
	./gradlew assembleRelease

clean: ## Clean build outputs
	./gradlew clean

## Test

test: ## Run all unit tests
	./gradlew test

lint: ## Run Android Lint and ShellCheck
	./gradlew lint
	shellcheck bin/*.sh

check: lint test ## Run lint and tests — the same gates CI enforces

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
