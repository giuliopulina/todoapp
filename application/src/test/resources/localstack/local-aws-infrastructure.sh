#!/bin/sh

awslocal sqs create-queue --queue-name stratospheric-app-todo-sharing-queue --region eu-west-2

awslocal dynamodb create-table \
    --table-name stratospheric-app-breadcrumb \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --provisioned-throughput ReadCapacityUnits=10,WriteCapacityUnits=10 \
    --region eu-west-2 \

echo "Initialized."