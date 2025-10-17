## Filter

In the CRTDL date and token are supported as filter types.
For all filters, the `name` field in the CRTDL corresponds to the `code` field in a FHIR Search parameter, which identifies how the filter should be applied.

### Date Filter

The date filter allows you to specify a date range for filtering resources.
It can be used to include or exclude resources based on their date attributes.

```json
{
  "type": "date",
  "name": "dateFilter",
  "parameters": {
    "start": "2023-01-01",
    "end": "2023-12-31"
  }
}
```

The `start` and `end` parameters define the range of dates to filter resources only **allow** a day wise granularity.

### Token Filter

Token filters allow you to filter resources based on specific codes or identifiers.
This is useful for filtering resources by specific concepts, such as LOINC codes or SNOMED codes.

```json
{
  "type": "token",
  "name": "codeFilter",
  "parameters": {
    "system": "http://loinc.org",
    "code": "12345-6"
  }
}

```

The `system` parameter specifies the coding system (e.g., LOINC, SNOMED), and the `code` parameter specifies the
specific code to filter by.
