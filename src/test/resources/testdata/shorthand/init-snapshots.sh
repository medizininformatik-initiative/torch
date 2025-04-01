
# Install dependencies
for dep in $(yq eval '.' sushi-config.yaml -o json | jq -r '.dependencies | to_entries | .[] | "\(.key)@\(.value)"'); do fhir install "$dep"; done

# inflate packages
for dep in $(yq eval '.' sushi-config.yaml -o json | jq -r '.dependencies | to_entries | .[] | "\(.key)@\(.value)"'); do fhir inflate --package "$dep"; done
