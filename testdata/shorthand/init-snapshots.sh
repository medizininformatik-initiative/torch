
# Install dependencies
for dep in $(yq eval '.' sushi-config.yaml -o json | jq -r '.dependencies | to_entries | .[] | "\(.key)@\(.value)"'); do printf "fhir package inflate $dep\n"; done
