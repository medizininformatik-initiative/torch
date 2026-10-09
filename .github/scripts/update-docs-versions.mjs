// Adds a docs version to versions.json on the gh-pages branch, consumed by the docs VersionSwitcher.
//
// Usage: node update-docs-versions.mjs <versions.json> <tag> <repo-name>
//
// Entries are sorted newest first (semver precedence, prereleases included), followed by "dev".
// "latest" marks the highest non-prerelease version independent of the order in which tags are
// deployed, so backfilling an older tag never takes it away. Prints "true" if <tag> is the latest
// version, i.e. its build also has to be deployed to the site root.

import {readFileSync, writeFileSync} from 'node:fs'

const [file, tag, repo] = process.argv.slice(2)

function parse(version) {
    const [core, ...pre] = version.replace(/^v/, '').split('-')
    const toIdentifiers = s => s.split('.').map(id => /^\d+$/.test(id) ? Number(id) : id)
    return {core: toIdentifiers(core), pre: pre.length ? toIdentifiers(pre.join('-')) : []}
}

function compareIdentifiers(a, b) {
    for (let i = 0; i < Math.max(a.length, b.length); i++) {
        if (a[i] === undefined) return -1
        if (b[i] === undefined) return 1
        if (a[i] === b[i]) continue
        if (typeof a[i] === typeof b[i]) return a[i] < b[i] ? -1 : 1
        return typeof a[i] === 'number' ? -1 : 1
    }
    return 0
}

function compareVersions(a, b) {
    const pa = parse(a)
    const pb = parse(b)
    const core = compareIdentifiers(pa.core, pb.core)
    if (core !== 0) return core
    if (!pa.pre.length || !pb.pre.length) return pb.pre.length - pa.pre.length
    return compareIdentifiers(pa.pre, pb.pre)
}

const isPrerelease = version => version.includes('-')

let versions = []
try {
    versions = JSON.parse(readFileSync(file, 'utf8')).map(entry => entry.version)
} catch {
    // First deployment: no versions.json yet.
}

const releases = [...new Set([...versions, tag])]
    .filter(version => version !== 'dev')
    .sort((a, b) => compareVersions(b, a))
const latest = releases.find(version => !isPrerelease(version))

const entries = [...releases, 'dev'].map(version => ({
    version,
    path: `/${repo}/${version}/`,
    latest: version === latest
}))

writeFileSync(file, JSON.stringify(entries, null, 2) + '\n')
console.log(tag === latest)
