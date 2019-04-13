#!/bin/bash

curl -sSL -X POST -H 'accept: application/json' -H 'Content-type: application/json' http://localhost:8080/stateless/login -d '{
    "username": "admin",
    "password": "admin"
}'
