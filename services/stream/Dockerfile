FROM python:3.12.2

RUN mkdir /app
COPY requirements.txt /app
COPY entrypoint.sh /app
RUN cd /app && python3 -mvenv venv && venv/bin/pip install -r requirements.txt

COPY *.py /app/


WORKDIR /app
ENV DB_HOST=postgres
ENTRYPOINT [ "/app/entrypoint.sh" ]
