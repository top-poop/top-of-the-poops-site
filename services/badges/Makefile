check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, DOCKER_HUB_ORG, dockerhub org name)

SERVICE=badges
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
	@test $(shell docker context show) = "totp"
	docker service create --with-registry-auth --name $(SERVICE) --network overlay-net $(FULL_NAME)

.PHONY: upgrade
upgrade:
	@test $(shell docker context show) = "totp"
	docker service update --with-registry-auth --image $(FULL_NAME) $(SERVICE)


PYTHON_DIR=venv/bin
PYTHON=$(PYTHON_DIR)/python
PIP=$(PYTHON_DIR)/pip
NPM=npm

DATAFILES=datafiles
BADGES=$(DATAFILES)

CONSTITUENCY_BADGES=$(BADGES)/constituency
HOME_BADGES=$(BADGES)/home
COMPANY_BADGES=$(BADGES)/company

$(PYTHON): .python_uptodate

.PHONY: python
python: $(PYTHON)

.python_uptodate: requirements.txt
	python3 -m venv venv
	$(PYTHON) -m pip install --upgrade pip
	$(PIP) install -r $<
	touch $@


TMP_CON_ORIG=tmp/constituencies
TMP_HOME_ORIG=tmp/home
TMP_COMPANY_ORIG=tmp/companies

CONSTITUENCY_ORIG=$(wildcard $(TMP_CON_ORIG)/*.png)
CONSTITUENCY_QUANT=$(foreach png,$(CONSTITUENCY_ORIG),$(CONSTITUENCY_BADGES)/$(notdir $(png)))

HOME_ORIG=$(wildcard $(TMP_HOME_ORIG)/*.png)
HOME_QUANT=$(foreach png,$(HOME_ORIG),$(HOME_BADGES)/$(notdir $(png)))

COMPANY_ORIG=$(wildcard $(TMP_COMPANY_ORIG)/*.png)
COMPANY_QUANT=$(foreach png,$(COMPANY_ORIG),$(COMPANY_BADGES)/$(notdir $(png)))

.PHONY: quantised
quantised: $(CONSTITUENCY_QUANT) $(HOME_QUANT) $(COMPANY_QUANT)

$(CONSTITUENCY_BADGES)/%.png: $(TMP_CON_ORIG)/%.png
	@mkdir -p $(@D)
	pngcrush $< $@

$(HOME_BADGES)/%.png: $(TMP_HOME_ORIG)/%.png
	@mkdir -p $(@D)
	pngcrush $< $@

$(COMPANY_BADGES)/%.png: $(TMP_COMPANY_ORIG)/%.png
	@mkdir -p $(@D)
	pngcrush $< $@

generate-company-badges:
	@mkdir -p $(TMP_COMPANY_ORIG)
	$(NPM) run save-elements http://localhost:8000/private/badges/companies $(TMP_COMPANY_ORIG)

generate-constituency-badges:
	@mkdir -p $(TMP_CON_ORIG)
	$(NPM) run save-elements http://localhost:8000/private/badges/constituencies $(TMP_CON_ORIG)

generate-home-badges:
	@mkdir -p $(TMP_HOME_ORIG)
	$(NPM) run save-elements http://localhost:8000/private/badges/home $(TMP_HOME_ORIG)



.PHONY: generated-data
generated-data:
