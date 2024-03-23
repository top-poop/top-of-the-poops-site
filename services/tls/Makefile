check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)
$(call check_defined, TOTP_CONTEXT, context)
$(call check_defined, MAXMIND_ACCOUNT_ID, maxmind id)
$(call check_defined, MAXMIND_LICENSE_KEY, maxmind key)

SERVICE=tls
SERVICE_VERSION=$(shell ../../bin/content-hash.sh nginx.conf Dockerfile docker-compose.yml nginx-files)

CURL=curl --fail
PYTHON=python3

IMAGE_NAME=totp-$(SERVICE)

LOCAL_NAME=$(IMAGE_NAME):$(SERVICE_VERSION)
FULL_NAME=$(DOCKER_HUB_ORG)/$(IMAGE_NAME):$(SERVICE_VERSION)

DATE=$(shell date +%Y%m%d)

.PHONY: check-context
check-context:
	@echo "Current docker context (expect 'default'):" $(shell docker context show)
	@test $(shell docker context show) = default


.maxmind-country-$(DATE).tar.gz:
	$(CURL) -L -u $(MAXMIND_ACCOUNT_ID):$(MAXMIND_LICENSE_KEY) -o $@ 'https://download.maxmind.com/geoip/databases/GeoLite2-Country/download?suffix=tar.gz'

.maxmind-country-$(DATE).mmdb:  .maxmind-country-$(DATE).tar.gz
	$(eval T := $(shell mktemp -d))
	tar -C $(T) -zxf $(shell pwd)/.maxmind-country-$(DATE).tar.gz
	cp $(T)/GeoLite2-Country_*/GeoLite2-Country.mmdb $@

GeoLite2-Country.mmdb: .maxmind-country-$(DATE).mmdb
	cp $< $@


.PHONY: image
image: check-context GeoLite2-Country.mmdb
	docker build -t nginx-totp:latest -f nginx-files/Dockerfile nginx-files
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
