
IMAGE_NAME=nginx-data
IMAGE_TAG = 0.1

.PHONY: image
image: generated-data
	docker build -t $(IMAGE_NAME):$(IMAGE_TAG) .

run: image
	docker run  -p 8081:80 $(IMAGE_NAME):$(IMAGE_TAG)

PYTHON_DIR=venv/bin
PYTHON=$(PYTHON_DIR)/python
PIP=$(PYTHON_DIR)/pip
GENERATE=generic.py

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

generated-data: generated-2021

generated-2021: $(DATA_2021_GENERATED)

$(DATAFILES)/v1/2021/%.json: sql/v1/2021/$(basename $(notdir %)).sql $(PYTHON) $(GENERATE)
	@mkdir -p $(dir $@)
	$(PYTHON) $(GENERATE) $< $@


.PHONY: constituencies
constituencies: constituencies.py constituencies.sql
	rm -rf $(DATAFILES)/constituencies
	@mkdir $(DATAFILES)/constituencies
	$(PYTHON) $< $(DATAFILES)/constituencies

.PHONY: clean
clean:
	rm -rf datafiles/v1/2021



