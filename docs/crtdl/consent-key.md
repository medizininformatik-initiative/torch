## Consent Key in CRTDL

The consent key in the Cohort Selection of the CRTDL specifies the consent rules that apply to the cohort definition
and data extraction. It enables per-patient, per-resource consent enforcement during structured extraction.

### How It Works

TORCH accepts MII combined consent keys (e.g. `yes-yes-no-yes`) and expands them to their individual MII OID provision
codes. Only codes defined in
[
`mappings/consent-code-config.json`](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/consent-code-config.json)
are evaluated — all others are silently ignored. The file can be edited manually; the codes it ships with are the
recommended MII default.

By default, TORCH evaluates:

- `2.16.840.1.113883.3.1937.777.24.5.3.8` — MDAT wissenschaftlich nutzen EU DSGVO NIVEAU (**required**)
-

`2.16.840.1.113883.3.1937.777.24.5.3.46` — MDAT retrospektiv wissenschaftlich nutzen EU DSGVO NIVEAU (optional modifier)

With the default config, any combined key that maps to `.8` is equivalent from TORCH's perspective. See
[Consent Handling](../implementation/consent.md) for the full evaluation logic.

### Example CRTDL with Consent Key

Consent keys are identified by `context.code = "Einwilligung"`. The key value is a `termCode` — in this example
`yes-yes-no-yes` from the `fdpg.consent.combined` system.

```json
{
  "version": "http://to_be_decided.com/draft-1/schema#",
  "display": "",
  "inclusionCriteria": [
    [
      {
        "context": {
          "code": "Einwilligung",
          "display": "Einwilligung",
          "system": "fdpg.mii.cds",
          "version": "1.0.0"
        },
        "termCodes": [
          {
            "code": "yes-yes-no-yes",
            "display": "Verteilte, EU-DSGVO konforme Analyse, ohne Krankenassendaten, und mit Rekontaktierung",
            "system": "fdpg.consent.combined"
          }
        ]
      }
    ]
  ]
}
```

### Consent Key Mapping

The full mapping from combined keys to individual MII OID codes is in
[
`mappings/consent-mappings_fhir.json`](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/consent-mappings_fhir.json).
The set of codes TORCH actually evaluates from that expansion is controlled separately by
`mappings/consent-code-config.json`.
