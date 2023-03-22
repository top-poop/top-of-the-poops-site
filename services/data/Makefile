check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)

SERVICE=data
SERVICE_VERSION = 0.2

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

.PHONY: image
run: image
	docker run  -p 8081:80 $(LOCAL_NAME)

.PHONY: push
push: check-context
	../../bin/docker-ensure-new-version.sh $(FULL_NAME)
	docker push $(FULL_NAME)

.PHONY: deploy
deploy:
	@test $(shell docker context show) = "totp"
	docker service create --with-registry-auth --name $(SERVICE) --network overlay-net $(FULL_NAME)

.PHONY: upgrade
upgrade:
	@test $(shell docker context show) = "totp"
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


DATA_2021_SQL=$(wildcard sql/v1/2021/*.sql)
DATA_2021_GENERATED=$(foreach sql,$(DATA_2021_SQL),$(DATAFILES)/v1/2021/$(basename $(notdir $(sql))).json)

GEO_2021_SQL=$(wildcard sql/v1/2021/geo/*.sql)
GEO_2021_GENERATED=$(foreach sql,$(GEO_2021_SQL),$(DATAFILES)/v1/2021/geo/$(basename $(notdir $(sql))).json)

generated-data: generated-2021

generated-2021: $(DATA_2021_GENERATED)

$(DATAFILES)/v1/2021/%.json: sql/v1/2021/$(basename $(notdir %)).sql $(PYTHON) $(SQL_TO_JSON)
	@mkdir -p $(dir $@)
	$(PYTHON) $(SQL_TO_JSON) $< $@

$(DATAFILES)/v1/2021/geo/%.json:  sql/v1/2021/geo/$(basename $(notdir %)).sql $(PYTHON) $(SQL_TO_TEXT)
	@mkdir -p $(dir $@)
	$(PYTHON) $(SQL_TO_TEXT) $< | jq -r tostring > $@



.PHONY: constituencies
constituencies: $(PYTHON) constituencies.py constituencies.sql
	rm -rf $(DATAFILES)/constituencies
	@mkdir $(DATAFILES)/constituencies
	$(PYTHON) constituencies.py $(DATAFILES)/constituencies

.PHONY: clean
clean:
	rm -rf datafiles/v1/2021
