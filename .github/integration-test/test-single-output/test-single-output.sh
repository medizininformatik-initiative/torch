#!/bin/bash -e

TORCH_URL=http://localhost:8086/fhir/\$extract-data
STATUS_URL=http://localhost:8086/fhir/__status
REQUEST=$(cat .github/integration-test/test-single-output/request-single-output.json)

response=$(curl -s -X POST $TORCH_URL \
               -H 'Content-Type: application/fhir+json' \
               -d "$REQUEST" --dump-header - -o /dev/null)

id=$(echo "$response" | awk -F'/' 'NR==2 {print $NF}')
id="${id%$'\r'}" # remove \r at the end of id

echo "Sent request to torch. Sleeping 5s to wait for result..."
sleep 5

for i in  1 .. 10
do
  echo "Requesting status $STATUS_URL/$id"
  response=$(curl -i -s "$STATUS_URL/$id")

  content_length=$(echo "$response" | grep -i "Content-Length" | awk '{print $2}')
  content_length="${content_length%$'\r'}" # remove \r at the end of content_length
  if [ "$content_length" -gt 0 ]; then
    break
  else
    echo "no content received from torch yet"
    sleep 5
  fi

  if [ "$i" == 9 ]; then
      break
    else
      echo "Fail 😞: No content received from torch after 10 tries"
      exit 1
    fi
done

body=$(echo "$response" | sed -n '/^\r$/,$p')
bundle_location=$(echo "$body" | jq -r '.output[0].url')

echo "Requesting Bundle at $bundle_location"
bundle=$(curl -s "$bundle_location")

EXPECTED_CODE="I95.0"
condition_code=$(echo "$bundle" | jq -r '.entry[0].resource.code.coding[0].code')
if [ "$condition_code" = "$EXPECTED_CODE" ]; then
  echo "OK 👍: condition code ($condition_code) in the extracted bundle equals the expected code"
else
  echo "Fail 😞: condition code ($condition_code) != $EXPECTED_CODE in the extracted bundle"
  exit 1
fi
