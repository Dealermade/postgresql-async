FROM postgres:9-alpine

ADD server.* /docker-entrypoint-initdb.d/
ADD init.sh /docker-entrypoint-initdb.d/init.sh
