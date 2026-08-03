\set ON_ERROR_STOP on

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L',
  :'preprod_user',
  :'preprod_password'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = :'preprod_user'
) \gexec

SELECT format(
  'ALTER ROLE %I LOGIN PASSWORD %L',
  :'preprod_user',
  :'preprod_password'
) \gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I',
  :'preprod_db',
  :'preprod_user'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = :'preprod_db'
) \gexec

SELECT format(
  'REVOKE ALL ON DATABASE %I FROM PUBLIC',
  :'preprod_db'
) \gexec

SELECT format(
  'GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I',
  :'preprod_db',
  :'preprod_user'
) \gexec
