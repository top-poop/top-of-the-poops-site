#!/bin/bash

image=$1

existing=$(docker buildx imagetools inspect $image 2>&1)

if [ $? -eq 1 ]
then
  exit 0
else
  echo $existing
  exit 1
fi




