# Development

## Release Checklist

* create a release branch called `release/v<version>` like `release/v1.1.0`
* rename every occurrence of the old version, say `1.0.0` or `1.1.0-SNAPSHOT` into the new version, say `1.1.0`
* update the CHANGELOG based on the milestone
* create a commit with the title `Release v<version>`
* create a PR from the release branch into the main branch
* merge that PR (after proper review)
* create and push a tag called `v<version>` like `v1.1.0` on the main branch at the merge commit
* change the version in the POM to the next SNAPSHOT version which usually increments the minor version, e.g. `1.2.0-SNAPSHOT`
* create release notes on GitHub

## Documentation

The documentation is published to GitHub Pages (`gh-pages` branch) in versions:

| Path                      | Content                              | Published by                          |
|---------------------------|--------------------------------------|---------------------------------------|
| `/torch/`                 | latest stable release                | `docs-release.yml` on release tags    |
| `/torch/<tag>/`           | every release, including prereleases | `docs-release.yml` on release tags    |
| `/torch/dev/`             | `main`                               | `docs.yml` on push to `main`          |
| `/torch/preview/pr-<nr>/` | pull request previews                | `docs.yml` on pull requests           |

`versions.json` in the root lists all versions for the version switcher and is maintained by
`.github/scripts/update-docs-versions.mjs`.

Pushing a release tag publishes its documentation automatically. To (re)build the documentation of
existing tags, e.g. for a backfill, run:

```sh
gh workflow run docs-release.yml -f tags="v1.0.0 v1.0.1"
```

The setup follows [Aether](https://github.com/medizininformatik-initiative/aether) with these deviations:

* The root serves the latest stable release itself instead of redirecting to it, so that links from before
  versioning stay valid. There is no separate `/stable/` copy.
* Prerelease tags are published automatically, but are never marked as latest.
* Latest is the highest stable version, independent of the order in which tags are deployed.
* The current version is injected at build time (`VITE_DOCS_VERSION`) instead of being parsed from the URL.
* Builds of older tags use the current theme, so they contain the version switcher as well.
