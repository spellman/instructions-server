# instructions-server



## TODO list to prep environment

1. Install postgresql.
2. Install TimescaleDB extension: http://docs.timescale.com/getting-started/installation?OS=linux&method=yum%2Fdnf
3. Make a database with the name from the config file.
```
postgres=# CREATE database instructions_server_dev;
=> CREATE DATABASE
```
4. Add TimescaleDB extension to database:
```
postgres=# \c instructions_server_dev
=> You are now connected to database "instructions_server_dev" as user "postgres".
instructions_server_dev=# CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;
=> CREATE EXTENSION
```
4. Run migrations.
```$ boot ragtime -m```
