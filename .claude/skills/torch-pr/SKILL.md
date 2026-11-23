---
name: torch-pr
description: Finish the work on a TORCH issue - check behaviour, docs and changelog, squash the branch into one commit on top of main, force-push it and open or update the pull request.
disable-model-invocation: true
---

# Finish an issue and open the pull request

Run this on the branch of the issue you work on, never on `main`.

## 1. Checklist

* The narrowest relevant tests pass per `.claude/skills/torch-testing/SKILL.md`. Report what you ran.
* Docs and changelog are up to date per `.claude/skills/torch-docs/SKILL.md`.

## 2. Check the branch

* `git branch --show-current` is the issue branch, not `main`.
* Commit all work for this issue first. `git status` must be clean, otherwise the rebase fails.
* `git log origin/main..HEAD` lists only commits for this issue. If it lists commits by other
  contributors, stop and ask before rewriting history.

## 3. Squash into one commit

Rebase first. Without the rebase, `reset --soft` stages a reversal of every commit that landed on `main`
after you branched, and the squash commit silently reverts them.

```bash
git fetch origin main
git rebase origin/main
git reset --soft origin/main
git commit -m "<issue title>" -m "Closes #<issue-number>"
```

If the rebase stops with conflicts, stop and ask. Do not resolve them silently.

* The commit subject is the issue title, unchanged. The body is only `Closes #<issue-number>`.
* No `Co-Authored-By` trailer and no other AI or tool attribution.
* Confirm that `git log origin/main..HEAD` shows exactly one commit.

## 4. Push and open the pull request

```bash
git push --force-with-lease
```

* Never push to `main`.
* If no PR exists, open one against `main` with `gh pr create --base main --title "<issue title>"`. The
  body is a short summary followed by a line `Closes #<issue-number>`. The PR title is identical to the
  issue title and the commit subject. No AI or tool attribution in the body.
* If the PR exists, the push updates it. Keep its title identical to the issue title.
* Stop at the PR. Do not merge it.
