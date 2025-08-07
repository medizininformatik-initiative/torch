---
# TORCH
---

> **TORCH** (Transfer Of Resources in Clinical Healthcare) is an open-source tool for privacy-aware, structure-guided
> extraction of clinical data from FHIR servers.

## 🔍 What is TORCH?

**TORCH** allows healthcare organizations and research institutions to extract structured clinical data using **CRTDL**
definitions (Clinical Resource Transfer Definition Language).

It supports privacy-aware filtering, consent enforcement, and can integrate into institutional data infrastructures via
FHIR, CQL, and proxy components.

---

## 🏥 Why Use TORCH?

- ✅ Extract only the data you need
- 🔐 Respect patient consent and redaction rules
- 📦 Output FHIR-compliant resources (NDJSON bundles)
- ⚙️ Configurable and scriptable for automation
- 🧠 Built with [HAPI FHIR](https://hapifhir.io/),
  supports [FLARE](https://github.com/medizininformatik-initiative/flare), [Blaze](https://github.com/samply/blaze), and
  more

---

## 🧭 Use Cases

- Federated medical research networks (e.g. MII Germany)
- Consent-aware data analytics

---

## 📚 Get Started

Ready to try TORCH?

👉 Head to [**How to Get Started**](./getting-started.md) for setup instructions and basic usage.

---

## 🛠️ Explore the Docs

Looking for environment variables, API details, or file formats?

👉 Visit the [**Documentation**](./documentation.md) for:

- ✅ Environment variables
- 📤 Output format
- 🔄 `$extract-data` API flow
- 🔒 Consent masking

---

## 💡 How It Works (Quick Summary)

1. You provide a **CRTDL JSON** describing *who* and *what* to extract.
2. TORCH evaluates the cohort using **FHIR Search** or **CQL**.
3. It queries the FHIR server(s), filters results, and applies masking.
4. Results are returned as **NDJSON bundles** (per patient), ready for import elsewhere.

---

## 🌐 Related Projects

- [CRTDL format](https://github.com/medizininformatik-initiative/clinical-resource-transfer-definition-language)
- [FLARE cohort server](https://github.com/medizininformatik-initiative/flare)
- [Blaze FHIR server](https://github.com/samply/blaze)
- [HAPI FHIR](https://hapifhir.io/)

---

## 📝 License

TORCH is released under the **Apache 2.0 License**.  
See [LICENSE](http://www.apache.org/licenses/LICENSE-2.0) for more information.
