
BUILD=./gradlew

SERVICE=gate
SERVICE_VERSION = 0.7

IMAGE_NAME=totp-$(SERVICE)

LOCAL_NAME=$(IMAGE_NAME):$(SERVICE_VERSION)
FULL_NAME=$(DOCKER_HUB_ORG)/$(IMAGE_NAME):$(SERVICE_VERSION)

.PHONY: test
test:
	$(BUILD) check

.PHONY: image
image:
	./gradlew jibDockerBuild


