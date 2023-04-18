FROM python:3.11-slim

COPY assets/ /run/assets
COPY dynamic-badge.py /run
COPY requirements.txt /run

RUN apt-get update && apt-get install -y fonts-roboto
RUN pip install -r /run/requirements.txt

WORKDIR /run
CMD python3.11 dynamic-badge.py

