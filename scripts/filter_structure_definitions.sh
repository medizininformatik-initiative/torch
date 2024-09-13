#!/bin/bash

# Input directory where the FHIR resources are stored (default to current dir)
INPUT_DIR="./output/fhir_resources"

# Output directory for StructureDefinitions
TARGET_DIR="./output/structuredefinitions"

# Directories to exclude
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
EXCLUDE_PATTERN=${EXCLUDE_PATTERN:1} # Remove leading pipe

# Loop over all JSON files in the input directory
echo "Filtering StructureDefinitions and excluding directories..."
find "$INPUT_DIR" -type f -name "*.json" | while read -r file; do
    if ! echo "$file" | grep -E "$EXCLUDE_PATTERN"; then
        # Check if the resourceType is StructureDefinition
        if grep -q '"resourceType": "StructureDefinition"' "$file"; then
            echo "Found StructureDefinition in $file"
            # Copy the file to the target directory
            cp "$file" "$TARGET_DIR/"
        else
            echo "Skipping $file (Not a StructureDefinition)"
        fi
    else
        echo "Excluding file $file"
    fi
done

echo "All StructureDefinitions have been copied to $TARGET_DIR"
