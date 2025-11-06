
# horrible hack really

localities.csv: ../../../../services/data/datafiles/v1/2024/localities.json
	tmp=$$(mktemp) && \
    	jq -r '.[].name1_text' $< > "$$tmp" && \
    	mv "$$tmp" $@ || rm -f "$$tmp"
