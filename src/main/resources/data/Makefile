
# horrible hack really

localities.csv: ../../../../services/data/datafiles/v1/2024/localities.json
	tmp=$$(mktemp) && \
    	jq -r '.[].name1_text' $< > "$$tmp" && \
    	mv "$$tmp" $@ || rm -f "$$tmp"

senedd-constituencies.csv: ../../../../services/data/datafiles/v1/2024/senedd-constituencies.json
	tmp=$$(mktemp) && \
    	jq -r '.[].english_na' $< > "$$tmp" && \
    	mv "$$tmp" $@ || rm -f "$$tmp"