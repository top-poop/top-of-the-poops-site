check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)

BUILD=./gradlew

SERVICE=pages
SERVICE_VERSION=$(shell find src Makefile build.gradle -type f -printf '%AF-%AH%AM %p\n' | sort -n | tail -1 | cut -d ' ' -f1)

IMAGE_NAME=totp-$(SERVICE)

LOCAL_NAME=$(IMAGE_NAME):$(SERVICE_VERSION)
FULL_NAME=$(DOCKER_HUB_ORG)/$(IMAGE_NAME):$(SERVICE_VERSION)

.PHONY: check-context
check-context:
	@echo "Current docker context (expect 'default'):" $(shell docker context show)
	@test $(shell docker context show) = default

.PHONY: test
test:
	$(BUILD) check

.PHONY: image
image: check-context
	./gradlew jibDockerBuild
	docker tag $(IMAGE_NAME):latest $(FULL_NAME)

.PHONY: push
push: check-context
	bin/docker-ensure-new-version.sh $(FULL_NAME)
	docker push $(FULL_NAME)

.PHONY: deploy
deploy:
	@test $(shell docker context show) = "totp"
	docker service create --with-registry-auth --name $(SERVICE) --network overlay-net $(FULL_NAME)

.PHONY: upgrade
upgrade:
	@test $(shell docker context show) = "totp"
	docker service update --with-registry-auth --image $(FULL_NAME) $(SERVICE)
