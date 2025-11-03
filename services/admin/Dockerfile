FROM ubuntu:noble


RUN apt update
RUN apt install -y iputils-ping socat tcpdump

RUN apt install -y postgresql-common
RUN echo | /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh
RUN apt -y install postgresql-client-17

CMD ["sleep", "infinity"]