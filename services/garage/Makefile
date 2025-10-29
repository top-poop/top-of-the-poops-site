check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)
$(call check_defined, TOTP_CONTEXT, context)

SERVICE=garage
SERVICE_VERSION=v2.1.0

IMAGE_NAME=dxflrs/garage

LOCAL_NAME=$(IMAGE_NAME):$(SERVICE_VERSION)
FULL_NAME=$(LOCAL_NAME)

.PHONY: image
image:
	true

.PHONY: run
run: image
	false

.PHONY: push
push:
	true

.PHONY: deploy
deploy:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker stack deploy --with-registry-auth -c docker-compose.yml garage

.PHONY: upgrade
upgrade:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker stack deploy --with-registry-auth -c docker-compose.yml garage
