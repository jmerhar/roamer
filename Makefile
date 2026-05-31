.PHONY: build build-release clean test debug install

## Build

build: ## Build debug APK
	./gradlew assembleDebug

build-release: ## Build release APK
	./gradlew assembleRelease

clean: ## Clean build outputs
	./gradlew clean

## Test

test: ## Run all unit tests
	./gradlew test

## Device

debug: ## Build and install debug app on connected device
	./gradlew installDebug

install: ## Build and install release APK on connected device
	./gradlew installRelease

## Help

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

.DEFAULT_GOAL := help
