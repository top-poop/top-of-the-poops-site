FROM python:3.12.2

RUN mkdir /app
COPY requirements.txt /app
RUN cd /app && python3 -mvenv venv && venv/bin/pip install -r requirements.txt

COPY *.py /app/


WORKDIR /app
ENV DB_HOST=postgres
ENV PYTHONUNBUFFERED=1
CMD venv/bin/python rainfall-populate.py --update
