#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username postgres --dbname postgres --set=resource_password="$RESOURCE_DB_PASSWORD" --set=workflow_password="$WORKFLOW_DB_PASSWORD" <<'SQL'
CREATE ROLE campus_resource LOGIN PASSWORD :'resource_password';
CREATE ROLE campus_workflow LOGIN PASSWORD :'workflow_password';
CREATE DATABASE fdu_study_room OWNER campus_resource;
CREATE DATABASE fdu_workflow OWNER campus_workflow;
SQL
