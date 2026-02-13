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


MAP_BASE=osm-latest-uk-ireland
MAP_MBTILES=$(MAP_BASE).mbtiles
MAP_PMTILES=$(MAP_BASE).pmtiles
MAP_OSM=$(MAP_BASE).osm.pbf

.PHONY: map
map: $(MAP_PMTILES)

clean-map:
	rm -f $(MAP_MBTILES) $(MAP_OSM) $(MAP_MBTILES).zst $(MAP_PMTILES)

TILEMAKER=downloads/tilemaker
PMTILES=downloads/pmtiles

OSM_SOURCE=planet-260202.osm.pbf

$(OSM_SOURCE):
	echo Download $@
	exit 1

$(MAP_MBTILES): $(MAP_OSM)
	java -Xmx48G -jar downloads/planetiler.jar \
      --download \
      --osm-path=$< \
      --output=$@ \
      --profile=openmaptiles \
      --zoom-min=0 \
      --zoom-max=14

$(MAP_OSM): $(OSM_SOURCE)
	time osmium extract -b -14.68,48.05,6.77,61.14 --strategy=complete_ways $< -o $@

%.pmtiles: %.mbtiles
	$(PMTILES) convert $< $@
