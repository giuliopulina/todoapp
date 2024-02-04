#!/bin/sh

awslocal sqs create-queue --queue-name todo-sharing

echo "Initialized."