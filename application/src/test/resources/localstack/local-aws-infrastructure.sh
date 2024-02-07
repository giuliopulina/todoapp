#!/bin/sh

awslocal sqs create-queue --queue-name todo-sharing --region eu-west-2

awslocal dynamodb create-table \
    --table-name todo-app-breadcrumb \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --provisioned-throughput ReadCapacityUnits=10,WriteCapacityUnits=10 \
    --region eu-west-2 \

echo "Initialized."