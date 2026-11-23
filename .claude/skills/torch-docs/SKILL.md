---
name: torch-docs
description: Update TORCH documentation for a change - user-facing docs pages, TORCH_* environment variables, error codes, CHANGELOG and ARCHITECTURE.md. Use when a change adds config, error codes or alters client-observable behaviour.
---

# Update the documentation for a change

The rules are in `AGENTS.md`, Documentation. This is the procedure.

## 1. Client-observable?

Decide whether the change alters what a client can observe: CRTDL semantics, endpoints, status codes,
output files, diagnostics. If it is unclear, ask.

## 2. New `TORCH_*` environment variable

Add an entry to `docs/configuration.md`, in the section where it belongs, in this format:

```markdown
#### `TORCH_EXAMPLE_SETTING` <Badge type="warning" text="Since 1.1.1"/>

What the setting does, and when to change it. Link to the implementation page if it helps.

**Default:** `7`

---
```

* The `Since` version is the GitHub milestone of the issue without the leading `v`
  (`gh issue view <issue-number> --json milestone`). If the issue has no milestone, ask.
* For a removed variable, keep the entry and add `<Badge type="danger" text="Removed"/>`.

## 3. New error code

Add a row to the table in `docs/implementation/error-codes.md` with the columns Error Code
(`MODULE_SUBMODULE_NUMBER`, next free number of the module), Meaning, Severity
(Info, Warning, Error, Critical), User Action / Fix and Developer Notes.

## 4. Behaviour change

Update the matching page under `docs/`:

| Page | Covers |
|---|---|
| `overview.md`, `index.md` | What TORCH is, landing page |
| `deployment.md` | Running TORCH and connecting it to Blaze, Flare and nginx |
| `configuration.md` | All environment variables |
| `crtdl/crtdl.md`, `crtdl/filter.md` | CRTDL format, attribute groups and filters |
| `api/api.md`, `api/task-api.md` | REST API (`$extract-data`, status, Task API) |
| `api/diagnostics.md` | Job diagnostics and report files |
| `implementation/implementation-overview.md` | Pipeline overview |
| `implementation/consent.md` | Consent handling |
| `implementation/direct-load.md`, `reference-resolve.md`, `cascading-delete.md`, `must-have.md` | The pipeline stages |
| `implementation/redaction.md`, `data-extraction.md`, `provenance.md`, `validation.md` | Redaction, extraction, provenance, CRTDL validation |
| `implementation/limitations.md` | Known limitations |
| `implementation/error-codes.md` | Error codes |

## 5. CHANGELOG.md

`CHANGELOG.md` is written at release time from the milestone (see `DEVELOPMENT.md`), not in the
feature PR. Instead:

* Make sure the issue has the milestone of the next release.
* Write the PR title so that it works as a changelog line: the release entry uses it with the PR link,
  under `Added`, `Changed`, `Fixed` or `Documentation`.

## 6. ARCHITECTURE.md and AGENTS.md

Update `ARCHITECTURE.md` and the Project Structure section of `AGENTS.md` if the pipeline, the job
lifecycle or component responsibilities change.

## 7. Check the build (optional)

If docs pages changed: `npm ci` (once) and `npm run docs:build` in `docs/`. The build fails on dead
links.
