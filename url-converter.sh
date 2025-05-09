#!/bin/bash -e

bundle="$1"

# Replace all reference values with the fullUrl of the referenced resource and set all request methods to 'POST'
jq '.entry as $entries | .entry =
       ($entries
           | map(
               walk(
                   if type == "object" then
                       with_entries(
                           if .key == "reference" then .value as $value | .value = ($entries[] | select(.request.url == $value) | .fullUrl)
                           else . end
                       )
                   else . end
               )
           )
           | map(.request.method = "POST")
       )' "$bundle"
