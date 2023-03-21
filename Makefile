
BUILD=./gradlew

.PHONY: test
test:
	$(BUILD) check

.PHONY: image
image:
	./gradlew jibDockerBuild


