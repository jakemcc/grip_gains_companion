SHELL := /bin/bash
.DEFAULT_GOAL := help

APP_ID := app.grip_gains_companion
MAIN_ACTIVITY := $(APP_ID)/.MainActivity
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ANDROID_SDK_ROOT ?= $(ANDROID_HOME)
GRADLEW := ./gradlew
ADB := adb
SERIAL_ARG := $(if $(SERIAL),-s $(SERIAL),)
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release.apk

export ANDROID_HOME
export ANDROID_SDK_ROOT

.PHONY: help test test-unit test-connected test-all assemble release bundle-release verify install install-release run clean

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*## "}; {printf "\033[36m%-16s\033[0m %s\n", $$1, $$2}'

test: ## Run unit tests
	$(GRADLEW) testDebugUnitTest

test-unit: ## Run unit tests
	$(GRADLEW) testDebugUnitTest

test-connected: ## Run connected Android tests; set SERIAL=<device-id> when needed
	$(if $(SERIAL),ANDROID_SERIAL=$(SERIAL) )$(GRADLEW) connectedDebugAndroidTest

test-all: ## Run unit and connected Android tests; set SERIAL=<device-id> when needed
	$(GRADLEW) testDebugUnitTest
	$(if $(SERIAL),ANDROID_SERIAL=$(SERIAL) )$(GRADLEW) connectedDebugAndroidTest

assemble: ## Build the debug APK
	$(GRADLEW) assembleDebug

release: ## Build the release APK
	$(GRADLEW) assembleRelease

bundle-release: ## Build the release app bundle
	$(GRADLEW) bundleRelease

verify: ## Run the standard verification command for this project
	$(GRADLEW) testDebugUnitTest assembleDebug

install: ## Build and install the debug app; set SERIAL=<device-id> when needed
	$(GRADLEW) assembleDebug
	$(ADB) $(SERIAL_ARG) install -r $(DEBUG_APK)

install-release: ## Build and install the release app; set SERIAL=<device-id> when needed
	$(GRADLEW) assembleRelease
	$(ADB) $(SERIAL_ARG) install -r $(RELEASE_APK)

run: ## Launch the app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) shell am start -n $(MAIN_ACTIVITY)

clean: ## Remove Gradle build outputs
	$(GRADLEW) clean
