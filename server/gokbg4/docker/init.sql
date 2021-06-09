select 1;

CREATE USER knowint WITH PASSWORD 'knowint';

DROP DATABASE if exists gokbg3dev;
CREATE DATABASE gokbg3dev;
GRANT ALL PRIVILEGES ON DATABASE gokbg3dev to knowint;
