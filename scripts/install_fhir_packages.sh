#!/bin/bash

# Set directories for FHIR CLI and where to store StructureDefinitions
TARGET_DIR="./output/fhir_resources"
FHIR_PACKAGE_LIST=(

  "de.medizininformatikinitiative.kerndatensatz.medikation 2.0.0"
  "de.medizininformatikinitiative.kerndatensatz.diagnose 2024.0.0"
  "de.medizininformatikinitiative.kerndatensatz.prozedur 2024.0.0"
  "de.medizininformatikinitiative.kerndatensatz.person 2024.0.0"
  "de.medizininformatikinitiative.kerndatensatz.biobank 1.0.8"
  "de.medizininformatikinitiative.kerndatensatz.molgen 1.0.0"
  "de.medizininformatikinitiative.kerndatensatz.consent 1.0.7"
  "de.medizininformatikinitiative.kerndatensatz.fall 2024.0.1"
  "de.medizininformatikinitiative.kerndatensatz.icu 2024.0.0-alpha2"
  "de.medizininformatikinitiative.kerndatensatz.laborbefund 1.0.6"
)

# Ensure the target directory exists
mkdir -p "$TARGET_DIR"

# Step 1: Install the list of FHIR packages
echo "Installing FHIR packages..."
for package in "${FHIR_PACKAGE_LIST[@]}"; do
    echo "Installing $package"
    # Split the package name and version
    pkg_name=$(echo "$package" | cut -d' ' -f1)
    version=$(echo "$package" | cut -d' ' -f2)

    # Attempt to install via FHIR CLI
    fhir install "$package" --here
    if [ $? -ne 0 ]; then
        echo "Failed to install $package using FHIR CLI, attempting npm fallback..."


        # If directory does not exist, try installing via npm again with verbose logging
        npm --registry https://packages.simplifier.net install  --install-strategy=shallow -prefix $TARGET_DIR  "$pkg_name@$version" --loglevel verbose

        if [ $? -ne 0 ]; then
            echo "Failed to install $package with npm after directory check"
            exit 1
        fi

    fi


done

# Step 2: Run the filter_structure_definitions.sh script to collect StructureDefinitions
echo "Running StructureDefinition filtering script..."
./scripts/filter_structure_definitions.sh

# Step 3: Confirm completion
echo "StructureDefinitions have been filtered and copied."
