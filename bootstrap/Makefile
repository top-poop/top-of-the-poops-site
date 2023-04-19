
it: ../src/main/resources/assets/css/totp.css ../src/main/resources/assets/css/totp.css.map

../src/main/resources/assets/css/totp.css: assets/css/totp.css
	cp $< $@

../src/main/resources/assets/css/totp.css.map: assets/css/totp.css.map
	cp $< $@

assets/css/totp.css assets/css/totp.css.map: scss/totp.scss package.json node_modules/.uptodate
	npm run build

node_modules/.uptodate: package.json
	npm install
	touch $@

