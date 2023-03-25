check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Undefined $1$(if $2, ($2))))

$(call check_defined, NRIA_LICENSE_KEY, dockerhub org name)

.PHONY: deploy
deploy:
	@test $(shell docker context show) = "totp"
	docker run -d --restart=always --name newrelic-infra --network=host --cap-add=SYS_PTRACE --privileged \
	--pid=host --cgroupns=host -v "/:/host:ro" -v "/var/run/docker.sock:/var/run/docker.sock" \
	-e  NRIA_LICENSE_KEY=$(NRIA_LICENSE_KEY) newrelic/infrastructure:latest
