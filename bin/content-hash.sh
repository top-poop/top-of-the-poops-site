#!/bin/bash

hashdeep -c sha1 -l -r -s $@ | sort | sha1sum | cut -c -10
