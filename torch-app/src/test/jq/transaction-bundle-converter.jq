.entry as $entries | .entry =
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
           | map(.request.url |= split("/")[0])
       )
