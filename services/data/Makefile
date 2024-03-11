check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)
$(call check_defined, TOTP_CONTEXT, context)

SERVICE=data
SERVICE_VERSION=$(shell ../../bin/content-hash.sh nginx.conf Dockerfile datafiles)

IMAGE_NAME=totp-$(SERVICE)

LOCAL_NAME=$(IMAGE_NAME):$(SERVICE_VERSION)
FULL_NAME=$(DOCKER_HUB_ORG)/$(IMAGE_NAME):$(SERVICE_VERSION)

.PHONY: check-context
check-context:
	@echo "Current docker context (expect 'default'):" $(shell docker context show)
	@test $(shell docker context show) = default

.PHONY: image
image: check-context generated-data
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
	docker service create --with-registry-auth --name $(SERVICE) --network overlay-net $(FULL_NAME)

.PHONY: upgrade
upgrade:
	@test $(shell docker context show) = $(TOTP_CONTEXT)
	docker service update --with-registry-auth --image $(FULL_NAME) $(SERVICE)


PYTHON_DIR=venv/bin
PYTHON=$(PYTHON_DIR)/python
PIP=$(PYTHON_DIR)/pip
SQL_TO_JSON=sql-to-json.py
SQL_TO_TEXT=sql-to-text.py

DATAFILES=datafiles

$(PYTHON): .python_uptodate

.PHONY: python
python: $(PYTHON)

.python_uptodate: requirements.txt
	python3 -m venv venv
	$(PYTHON) -m pip install --upgrade pip
	$(PIP) install -r $<
	touch $@

DATAFILES_2022=$(DATAFILES)/v1/2022

DATA_2022_SQL=$(wildcard sql/v1/2022/*.sql)
DATA_2022_GENERATED=$(foreach sql,$(DATA_2022_SQL),$(DATAFILES_2022)/$(basename $(notdir $(sql))).json)

DATA_2022_LIVE=$(DATAFILES_2022)/spills-live-overflows-by-rainfall-thames-water.json $(DATAFILES_2022)/spills-live-summary-thames-water.json

GEO_2022_SQL=$(wildcard sql/v1/2022/geo/*.sql)
GEO_2022_GENERATED=$(foreach sql,$(GEO_2022_SQL),$(DATAFILES_2022)/geo/$(basename $(notdir $(sql))).json)

PROVIDED=datafiles/media-appearances.json datafiles/water-companies.json $(DATAFILES_2022)/geo/countries.json

generated-data: generated-2022 generated-live $(DATAFILES)/constituencies/.uptodate $(DATAFILES)/beaches/.uptodate $(DATAFILES)/shellfisheries/.uptodate $(PROVIDED)

generated-2022: $(DATA_2022_GENERATED) $(GEO_2022_GENERATED) $(PROVIDED)

provided: $(PROVIDED)

.PHONY: generated-live
generated-live:
	$(PYTHON) thames-summary.py


.PHONY: .FORCE
.FORCE:

$(DATA_2022_LIVE): .FORCE

$(DATAFILES)/v1/2022/%.json: sql/v1/2022/$(basename $(notdir %)).sql $(PYTHON) $(SQL_TO_JSON)
	@mkdir -p $(dir $@)
	$(PYTHON) $(SQL_TO_JSON) $< $@

$(DATAFILES)/v1/2022/geo/%.json:  sql/v1/2022/geo/$(basename $(notdir %)).sql $(PYTHON) $(SQL_TO_TEXT)
	@mkdir -p $(dir $@)
	$(PYTHON) $(SQL_TO_TEXT) $< | jq -r tostring > $@


$(DATAFILES)/constituencies/.uptodate: $(PYTHON) geometry.py constituencies.sql
	rm -rf $(DATAFILES)/constituencies
	@mkdir $(DATAFILES)/constituencies
	$(PYTHON) geometry.py constituencies.sql $(DATAFILES)/constituencies
	touch $@

$(DATAFILES)/beaches/.uptodate: $(PYTHON) geometry.py beaches.sql
	rm -rf $(DATAFILES)/beaches
	@mkdir $(DATAFILES)/beaches
	$(PYTHON) geometry.py beaches.sql $(DATAFILES)/beaches
	touch $@

$(DATAFILES)/shellfisheries/.uptodate: $(PYTHON) geometry.py shellfisheries.sql
	rm -rf $(DATAFILES)/shellfisheries
	@mkdir $(DATAFILES)/shellfisheries
	$(PYTHON) geometry.py shellfisheries.sql $(DATAFILES)/shellfisheries
	touch $@


$(DATAFILES)/media-appearances.json: provided/media-appearances.json
	cp $< $@

$(DATAFILES)/water-companies.json: provided/water-companies.json
	cp $< $@

$(DATAFILES)/v1/2022/geo/countries.json: provided/countries.json
	cp $< $@

.PHONY: clean-2022
clean-2022:
	rm -rf datafiles/v1/2022

.PHONY: beaches-again
beaches-again:
	rm -rf datafiles/beaches
	$(MAKE) generated-data


.PHONY: generated-2022-again
generated-2022-again: clean-2022 generated-2022
