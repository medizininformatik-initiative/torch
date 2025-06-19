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
