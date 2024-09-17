#!/bin/bash

# Set directories for FHIR CLI and where to store StructureDefinitions
TARGET_DIR="./output/fhir_resources"
DEPENDENCIES_DIR="./dependencies"
FHIR_PACKAGE_LIST=(
  "de.medizininformatikinitiative.kerndatensatz.laborbefund 1.0.6"
  "de.medizininformatikinitiative.kerndatensatz.medikation 2.0.0"
  "de.medizininformatikinitiative.kerndatensatz.diagnose 2024.0.0"
  "de.medizininformatikinitiative.kerndatensatz.prozedur 2024.0.0"
  "de.medizininformatikinitiative.kerndatensatz.person 2024.0.0"
  "de.medizininformatikinitiative.kerndatensatz.biobank 1.0.8"
  "de.medizininformatikinitiative.kerndatensatz.molgen 1.0.0"
  "de.medizininformatikinitiative.kerndatensatz.consent 1.0.7"
  "de.medizininformatikinitiative.kerndatensatz.fall 2024.0.1"
  "de.medizininformatikinitiative.kerndatensatz.icu 2024.0.0-alpha2"
)

# Ensure the target directory exists
mkdir -p "$TARGET_DIR"

# Step 1: Install the list of FHIR packages
echo "Installing FHIR packages..."
for package in "${FHIR_PACKAGE_LIST[@]}"; do
    echo "Installing $package"
    # Split the package name and version
    pkg_name=$(echo "$package" | cut -d' ' -f1)
    pkg_version=$(echo "$package" | cut -d' ' -f2)
    # Log the command to the console
    echo "Running command: fhir install $pkg_name $pkg_version --here"
    # Install via FHIR CLI without quotes
    sudo fhir install $pkg_name $pkg_version --here
done

# Step 2: Call the separate script for inflating packages and copying files
./inflate_and_copy.sh

# Step 3: Confirm completion
echo "StructureDefinitions have been filtered and copied."
