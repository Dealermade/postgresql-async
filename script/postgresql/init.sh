#!/usr/bin/env bash

psql -d "postgres" -c 'create database netty_driver_test;' -U $POSTGRES_USER
psql -d "postgres" -c 'create database netty_driver_time_test;' -U $POSTGRES_USER
psql -d "postgres" -c "alter database netty_driver_time_test set timezone to 'GMT'" -U $POSTGRES_USER
psql -d "netty_driver_test" -c "create table transaction_test ( id varchar(255) not null, constraint id_unique primary key (id))" -U $POSTGRES_USER
psql -d "postgres" -c "CREATE USER postgres_md5 WITH PASSWORD 'postgres_md5'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_md5;" -U $POSTGRES_USER
psql -d "postgres" -c "CREATE USER postgres_cleartext WITH PASSWORD 'postgres_cleartext'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_cleartext;" -U $POSTGRES_USER
psql -d "postgres" -c "CREATE USER postgres_kerberos WITH PASSWORD 'postgres_kerberos'; GRANT ALL PRIVILEGES ON DATABASE netty_driver_test to postgres_kerberos;" -U $POSTGRES_USER
psql -d "netty_driver_test" -c "CREATE TYPE example_mood AS ENUM ('sad', 'ok', 'happy');" -U $POSTGRES_USER

psql -d "postgres" -c 'create schema test_search_path;' -U $POSTGRES_USER
psql -d "postgres" -c 'create table test_search_path.in_search_path_table ( id varchar(255) not null, constraint id_unique primary key (id));' -U $POSTGRES_USER

echo "pg_hba.conf goes as follows"
cat "$PGDATA/pg_hba.conf"

echo "local    all             all                                     trust"    >  $PGDATA/pg_hba.conf
echo "host     all             postgres           all                  trust"    >> $PGDATA/pg_hba.conf
echo "host     all             postgres_md5       all                  md5"      >> $PGDATA/pg_hba.conf
echo "host     all             postgres_cleartext all                  password" >> $PGDATA/pg_hba.conf

echo "pg_hba.conf is now like"
cat "$PGDATA/pg_hba.conf"

cp -r /docker-entrypoint-initdb.d/server.* $PGDATA/
chmod 600 $PGDATA/server.crt
chmod 600 $PGDATA/server.key