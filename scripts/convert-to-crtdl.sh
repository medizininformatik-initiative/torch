#!/bin/bash

# Function to convert input JSON to the target format
convert_to_crtdl() {
  input_file="$1"

  if [[ ! -f "$input_file" ]]; then
    echo "Error: File not found!"
    exit 1
  fi

  # Read the input JSON from the file
  input_json=$(cat "$input_file")

  # Use jq to create the new JSON structure and filter for date from 2020 to 2025
  crtdl_json=$(echo "$input_json" | jq '

      {
        "version": "http://json-schema.org/to-be-done/schema#",
        "display": "",
        "cohortDefinition": {
          "version": "http://to_be_decided.com/draft-1/schema#",
          "display": "",
          "inclusionCriteria": [
            [
              {
                "termCodes": [
                  {
                    "code": "263495000",
                    "system": "http://snomed.info/sct",
                    "display": "Geschlecht"
                  }
                ],
                "context": {
                  "code": "Patient",
                  "system": "fdpg.mii.cds",
                  "version": "1.0.0",
                  "display": "Patient"
                },
                "valueFilter": {
                  "selectedConcepts": [
                    {
                      "code": "female",
                      "display": "Female",
                      "system": "http://hl7.org/fhir/administrative-gender"
                    },
                    {
                      "code": "male",
                      "display": "Male",
                      "system": "http://hl7.org/fhir/administrative-gender"
                    }
                  ],
                  "type": "concept"
                }
              }
            ]
          ]
        },
      "dataExtraction": {
        "attributeGroups": [
          .[] | {
            "groupReference": .url,
            "attributes": (.fields | map({ "attributeRef": .id, "mustHave": false })),
            "filter": (
              if .filters then
                .filters[] | select(.type == "date") | [{
                  "type": "date",
                  "name": .name,
                  "start": "2020",
                  "end": "2025"
                }]
              else []
              end
            )
          }
        ]
      }
    }
  ')

  # Output the final JSON to a file
  echo "$crtdl_json" > output_crtdl.json
  echo "Converted JSON has been written to output_crtdl.json"
}

# Check if the user provided an input file
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <input_json_file>"
  exit 1
fi

# Call the function with the input file
convert_to_crtdl "$1"
