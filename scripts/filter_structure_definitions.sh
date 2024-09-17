#!/bin/bash

# Set the root directories
ROOT_DIR="./dependencies"
TARGET_DIR="./output/structuredefinitions"

# Directories to exclude (these are relative to the ROOT_DIR)
EXCLUDE_DIRS=(
  "de.basisprofil.r4#1.3.2"
  "de.basisprofil.r4#1.4.0"
  "de.basisprofil.r4#1.5.0-ballot2"
  "de.medizininformatikinitiative.kerndatensatz.meta#1.0.3"
  "hl7.fhir.r4.core#4.0.1"
  "hl7.fhir.uv.ips#1.0.0"
  "hl7.fhir.uv.genomics-reporting#2.0.0"
  "hl7.terminology.r4#5.0.0"
  "de.einwilligungsmanagement#1.0.1"
)

# Ensure the target directory exists
mkdir -p "$TARGET_DIR"

# Construct the exclusion pattern for grep
EXCLUDE_PATTERN=$(printf "|%s" "${EXCLUDE_DIRS[@]}")
EXCLUDE_PATTERN=${EXCLUDE_PATTERN:1} # Remove the leading pipe

# Function to generate a good filename
generate_filename() {
    local file=$1
    local resource_id=$(jq -r '.id' "$file")  # Extract the resource id using jq
    local timestamp=$(date +"%Y%m%d-%H%M%S") # Get the current timestamp
    local base_name=$(basename "$file" .json) # Get the base filename without extension

    # Construct the filename based on id and timestamp, fall back to base_name if no id
    if [ "$resource_id" != "null" ]; then
        echo "${resource_id}_${timestamp}.json"
    else
        echo "${base_name}_${timestamp}.json"
    fi
}

# Function to handle the FHIR operations on the file path
perform_fhir_operations() {
    local file=$1

    # Perform the sequence of fhir commands if the file is a StructureDefinition
    if grep -q '"resourceType": "StructureDefinition"' "$file"; then
        echo "Processing StructureDefinition in $file"

        # Push the file to FHIR
        fhir push "$file"

        # Generate snapshot
        fhir snapshot

        # Generate a good filename
        filename=$(generate_filename "$file")

        # Save the result with the generated filename to the target directory
        fhir save "$TARGET_DIR/$filename"

        echo "Saved processed StructureDefinition from $file to $TARGET_DIR/$filename"
    else
        echo "No operation performed for $file (not a StructureDefinition)"
    fi
}

# Function to process each file, ignoring excluded directories
process_files() {
    local dir=$1

    # Find all JSON files in the directory, excluding those in the EXCLUDE_DIRS
    find "$dir" -type f -name "*.json" | while read -r file; do
        # Check if the file is part of an excluded directory
        if ! echo "$file" | grep -E "$EXCLUDE_PATTERN"; then
            # Get the relative path
            relative_path=$(realpath --relative-to="$PWD" "$file")

            # Perform the FHIR operations based on the file
            perform_fhir_operations "$relative_path"
        fi
    done
}

# Traverse the directories and call FHIR operations on each JSON file
for dir in "$ROOT_DIR"/*/; do
    process_files "$dir"
done

# Final message
echo "FHIR operations completed on all relevant files."
