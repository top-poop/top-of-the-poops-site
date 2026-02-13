check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)
$(call check_defined, TOTP_CONTEXT, context)

SERVICE=tiles
SERVICE_VERSION=$(shell ../../bin/content-hash.sh nginx.conf Dockerfile config.json styles/*)

IMAGE_NAME=totp-$(SERVICE)

LOCAL_NAME=$(IMAGE_NAME):$(SERVICE_VERSION)
FULL_NAME=$(DOCKER_HUB_ORG)/$(IMAGE_NAME):$(SERVICE_VERSION)

.PHONY: check-context
check-context:
	@echo "Current docker context (expect 'default'):" $(shell docker context show)
	@test $(shell docker context show) = default

image: check-context
	docker build -t $(LOCAL_NAME)  -t $(FULL_NAME) .

.PHONY: run
run: image
	docker run  -p 8081:80 --mount type=bind,source=/home/richja/tmp/tileserver,target=/data/map $(LOCAL_NAME)

.PHONY: push
push:
	../../bin/docker-ensure-new-version.sh $(FULL_NAME)
	docker push $(FULL_NAME)

.PHONY: deploy
deploy:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker service create --with-registry-auth --name $(SERVICE) --network overlay-net \
	--mount type=bind,source=/data/tiles,target=/data/map $(FULL_NAME)

.PHONY: upgrade
upgrade:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker service update --with-registry-auth --image $(FULL_NAME) $(SERVICE)


.PHONY: map
map: osm-latest-uk-ireland.mbtiles

TILEMAKER=downloads/tilemaker
PMTILES=downloads/pmtiles

OSM_SOURCE=planet-260202.osm.pbf

$(OSM_SOURCE):
	echo Download $@
	exit 1

osm-latest-uk-ireland.osm.pbf: $(OSM_SOURCE)
	osmconvert $< -b=-14.68,48.05,6.77,61.14 --complete-ways --complete-multipolygons --complete-boundaries -o=$@

# this takes 78GB ram
%.mbtiles: %.osm.pbf
	$(TILEMAKER) --input $< --output $@ --process tm-resources/process-openmaptiles.lua --config tm-resources/config-openmaptiles.json

%.pmtiles: %.mbtiles
	$(PMTILES) convert $< $@
