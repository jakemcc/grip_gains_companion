SHELL := /bin/bash
.DEFAULT_GOAL := help

APP_ID := app.grip_gains_auto
MAIN_ACTIVITY := $(APP_ID)/.MainActivity
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ANDROID_SDK_ROOT ?= $(ANDROID_HOME)
GRADLEW := ./gradlew
ADB := adb
APKSIGNER := $(lastword $(sort $(wildcard $(ANDROID_HOME)/build-tools/*)))/apksigner
SERIAL_ARG := $(if $(SERIAL),-s $(SERIAL),)
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_METADATA := app/build/outputs/apk/release/output-metadata.json
RELEASE_APK = app/build/outputs/apk/release/$(shell sed -n 's/.*"outputFile": "\(.*\)".*/\1/p' $(RELEASE_METADATA) | head -n 1)
SIGNED_RELEASE_APK := app/build/outputs/apk/release/app-release-signed.apk
RELEASE_OUTPUT_APK := grip-gains-auto.apk
DEBUG_KEYSTORE := $(HOME)/.android/debug.keystore
DEBUG_KEY_ALIAS := androiddebugkey
DEBUG_KEY_PASSWORD := android

export ANDROID_HOME
export ANDROID_SDK_ROOT

.PHONY: help devices test test-unit test-connected test-all assemble release signed-release bundle-release verify install install-release push-release run clean

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*## "}; {printf "\033[36m%-16s\033[0m %s\n", $$1, $$2}'

devices: ## List connected Android devices
	$(ADB) devices -l

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

signed-release: release ## Build the signed release APK as grip-gains-auto.apk
	$(APKSIGNER) sign --v4-signing-enabled false --ks $(DEBUG_KEYSTORE) --ks-key-alias $(DEBUG_KEY_ALIAS) --ks-pass pass:$(DEBUG_KEY_PASSWORD) --key-pass pass:$(DEBUG_KEY_PASSWORD) --out $(RELEASE_OUTPUT_APK) $(RELEASE_APK)

bundle-release: ## Build the release app bundle
	$(GRADLEW) bundleRelease

verify: ## Run the standard verification command for this project
	$(GRADLEW) testDebugUnitTest assembleDebug

install: ## Build and install the debug app; set SERIAL=<device-id> when needed
	$(GRADLEW) assembleDebug
	$(ADB) $(SERIAL_ARG) install -r $(DEBUG_APK)

install-release: ## Build and install the release app; set SERIAL=<device-id> when needed
	$(GRADLEW) assembleRelease
	$(APKSIGNER) sign --ks $(DEBUG_KEYSTORE) --ks-key-alias $(DEBUG_KEY_ALIAS) --ks-pass pass:$(DEBUG_KEY_PASSWORD) --key-pass pass:$(DEBUG_KEY_PASSWORD) --out $(SIGNED_RELEASE_APK) $(RELEASE_APK)
	$(ADB) $(SERIAL_ARG) install -r $(SIGNED_RELEASE_APK)

push-release: install-release ## Alias for building and installing the release app

run: ## Launch the app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) shell am start -n $(MAIN_ACTIVITY)

clean: ## Remove Gradle build outputs
	$(GRADLEW) clean
