
it: ../src/main/resources/assets/css/totp.css ../src/main/resources/assets/css/totp.css.map ../src/main/resources/assets/js/bootstrap.min.js ../src/main/resources/assets/js/bootstrap.min.js.map

../src/main/resources/assets/js/bootstrap.min.js: node_modules/bootstrap/dist/js/bootstrap.min.js
	cp $< $@

../src/main/resources/assets/js/bootstrap.min.js.map: node_modules/bootstrap/dist/js/bootstrap.min.js.map
	cp $< $@

../src/main/resources/assets/css/totp.css: assets/css/totp.css
	cp $< $@

../src/main/resources/assets/css/totp.css.map: assets/css/totp.css.map
	cp $< $@

assets/css/totp.css assets/css/totp.css.map: scss/totp.scss package.json node_modules/.uptodate
	npm run build

node_modules/.uptodate: package.json
	npm install
	touch $@


.PHONY: watch
watch:
	$(MAKE) it
	while true; \
	do \
			inotifywait -r scss; \
			$(MAKE) it; \
	done
