#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <replica-count>"
  exit 1
fi

REPLICAS=$1

yq eval ".spec.replicas = ${REPLICAS}" -i src/main/resources/crds/nt-example.yaml
kubectl apply -f src/main/resources/crds/nt-example.yaml