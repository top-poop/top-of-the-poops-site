check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)
$(call check_defined, TOTP_CONTEXT, context)


SERVICE=tls
SERVICE_VERSION=$(shell ../../bin/content-hash.sh nginx.conf Dockerfile docker-compose.yml)

CURL=curl --verbose --fail --silent

IMAGE_NAME=totp-$(SERVICE)

LOCAL_NAME=$(IMAGE_NAME):$(SERVICE_VERSION)
FULL_NAME=$(DOCKER_HUB_ORG)/$(IMAGE_NAME):$(SERVICE_VERSION)

.PHONY: check-context
check-context:
	@echo "Current docker context (expect 'default'):" $(shell docker context show)
	@test $(shell docker context show) = default


.geoip.dat.gz:
	$(CURL) -o $@ http://geolite.maxmind.com/download/geoip/database/GeoLiteCountry/GeoIP.dat.gz

.geoip.dat: .geoip.dat.gz
	rm -f $@
	zcat $< > $@

geoip.dat: .geoip.dat
	mv $< $@


.PHONY: image
image: check-context
	docker build -t $(LOCAL_NAME)  -t $(FULL_NAME) .

.PHONY: run
run: image
	docker run  -p 8081:80 $(LOCAL_NAME)

.PHONY: push
push: check-context
	../../bin/docker-ensure-new-version.sh $(FULL_NAME)
	docker push $(FULL_NAME)

.PHONY: deploy
deploy:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	SERVICE_FULL_NAME=$(FULL_NAME) docker compose up -d tls

.PHONY: upgrade
upgrade: deploy
