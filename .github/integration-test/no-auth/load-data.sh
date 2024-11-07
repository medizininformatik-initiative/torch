#!/bin/bash -e

DIR="$1"

blazectl --no-progress --server http://localhost:8082/fhir upload "$DIR"
