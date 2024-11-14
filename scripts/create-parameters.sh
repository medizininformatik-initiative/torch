#!/bin/bash -e

#
# Creates a FHIR Parameters resource by reading the CRTDL from a file specified
# in the first argument of this script and outputs it on STDOUT. Can be used to
# generate the input of the $extract-data operation.
#
# Usage: create-parameters.sh crtdl.json | curl -s 'http://localhost:8080/fhir/$extract-data' -H "Content-Type: application/fhir+json" -d @-
#

CRTDL_FILE="$1"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "crtdl"
    }
  ]
}
END
}

parameters | jq --arg content "$(cat "$CRTDL_FILE")" -cM '.parameter[0].valueBase64Binary = ($content | @base64)'
