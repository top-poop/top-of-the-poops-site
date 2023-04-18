FROM python:3.11-slim

RUN apt-get update && apt-get install -y fonts-roboto

COPY requirements.txt /run
RUN pip install -r /run/requirements.txt

COPY assets/ /run/assets
COPY dynamic-badge.py /run

WORKDIR /run
CMD python3.11 dynamic-badge.py

