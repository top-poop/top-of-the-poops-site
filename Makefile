check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)
$(call check_defined, TOTP_CONTEXT, context)


BUILD=./gradlew

SERVICE=pages
SERVICE_VERSION=$(shell bin/content-hash.sh bin src *gradle*)

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
	./gradlew check jibDockerBuild
	docker tag $(IMAGE_NAME):latest $(FULL_NAME)

.PHONY: push
push: check-context
	bin/docker-ensure-new-version.sh $(FULL_NAME)
	docker push $(FULL_NAME)

.PHONY: deploy
deploy:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker service create --with-registry-auth --name $(SERVICE) --network overlay-net --env DB_HOST=postgres --env REDIS_HOST=redis $(FULL_NAME)

.PHONY: test-deploy
test-deploy:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker service create --with-registry-auth --name $(SERVICE)-test --network overlay-net --env DB_HOST=postgres --env REDIS_HOST=redis $(FULL_NAME)

.PHONY: upgrade
upgrade:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker service update --with-registry-auth --image $(FULL_NAME) --env-add DB_HOST=postgres --env-add REDIS_HOST=redis $(SERVICE)

.PHONY: upgrade
test-upgrade:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker service update --with-registry-auth --image $(FULL_NAME) --env-add DB_HOST=postgres --env-add REDIS_HOST=redis $(SERVICE)-test
