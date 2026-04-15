# Story INFRA-1.0: Establish noometric-llc GitHub Account

## Status

Complete

## Story

**As** Noometric LLC's founder,
**I want** the `newsanalyzer-admin` GitHub account renamed to `noometric-llc` with the Noometric email address attached,
**so that** all company repositories live under the correct brand identity and the old `newsanalyzer-admin` name is retired.

## Context

This is manual GitHub account administration — no code changes required.

**Current state:**
- `newsanalyzer-admin` (email: newsanalyzer.steve@gmail.com) — personal GitHub account that owns `news-analyzer`
- `sowood` (email: sowood@cox.net) — personal GitHub account for general dev work

**Target state:**
- `noometric-llc` (same account as `newsanalyzer-admin`, renamed) — owns all Noometric repositories
- Primary or secondary email updated to `steven.kosuthwood@noometric.com`

**Note on account type:** This remains a personal GitHub account, not a GitHub Organization. For a solo founder, the practical difference is negligible — repos appear at `github.com/noometric-llc/` either way. Converting to a proper org is a future option if/when hiring.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | GitHub username changed from `newsanalyzer-admin` to `noometric-llc` |
| AC2 | `steven.kosuthwood@noometric.com` is added as an email on the account |
| AC3 | Repo is accessible at `github.com/noometric-llc/news-analyzer` |
| AC4 | Local git remote in `NewsAnalyzer_V2` clone updated to new URL |
| AC5 | `git fetch` and `git push` work correctly against the new remote URL |
| AC6 | `noometric-intelligence` private repo created under `noometric-llc` |

## Tasks / Subtasks

- [X] Task 1: Change GitHub username (AC1, AC3)
  - [X] Log in to `newsanalyzer-admin` account on GitHub
  - [X] Go to Settings → Account → Change username
  - [X] New username: `noometric-llc`
  - [X] Confirm the change (GitHub will warn about redirect behavior — accept)
  - [X] Verify: `github.com/noometric-llc/news-analyzer` loads the repo

- [X] Task 2: Add Noometric email (AC2)
  - [X] Settings → Emails → Add email address
  - [X] Add `steven.kosuthwood@noometric.com`
  - [X] Verify the email address (GitHub will send a confirmation)
  - [X] Optional: set as primary email so it appears on future commits

- [x] Task 3: Update local git remote (AC4, AC5)
  - [X] In the `NewsAnalyzer_V2` local clone:
    ```bash
    git remote set-url origin https://github.com/noometric-llc/news-analyzer.git
    ```
  - [X] Verify: `git remote -v`
  - [X] Verify: `git fetch origin` succeeds

- [X] Task 4: Create `noometric-intelligence` private repo (AC6)
  - [X] While logged in as `noometric-llc`: New repository
  - [X] Name: `noometric-intelligence`
  - [X] Visibility: **Private**
  - [X] Initialize with a README:
    ```
    # noometric-intelligence

    Proprietary AI evaluation intelligence layer for Noometric LLC.
    ```
  - [ ] No license file yet — that gets added in INFRA-1.1

## Dev Notes

### GitHub Redirect Behavior After Username Change

After renaming, GitHub automatically redirects `github.com/newsanalyzer-admin/*` to `github.com/noometric-llc/*` for an indeterminate grace period. This means existing bookmarks and any CI/CD references won't hard-break immediately — but update the local remote (Task 3) right away and don't rely on the redirect permanently.

### Two-Account Workflow (noometric-llc vs. sowood)

You now have two GitHub accounts:
- `noometric-llc` — owns all Noometric company repos (`news-analyzer`, `noometric-intelligence`)
- `sowood` — personal account

**Browser:** Use two browser profiles (or a private window) to stay logged into both simultaneously.

**Git operations:** If you clone `noometric-intelligence` while logged in as `sowood`, you'll need to authenticate as `noometric-llc` for that repo. Options:
- Use HTTPS with a Personal Access Token for the `noometric-llc` account
- Configure an SSH key alias (see below)

**SSH key alias pattern** (optional but clean):

In `~/.ssh/config`:
```
# Personal account
Host github.com-sowood
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_rsa_sowood

# Noometric account
Host github.com-noometric
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_rsa_noometric
```

Then clone using the alias:
```bash
git clone git@github.com-noometric:noometric-llc/noometric-intelligence.git
```

For most day-to-day work on `news-analyzer` (which is public), HTTPS auth is fine.

## Dev Agent Record

### Agent Model Used

N/A — Manual steps only

### Completion Notes

_To be filled in after completion_

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-04-14 | 1.0 | Initial story draft | Sarah (PO) |
| 2026-04-14 | 1.1 | Revised: rename existing newsanalyzer-admin account instead of creating new org | Sarah (PO) |
| 2026-04-14 | 1.2 | Completed all items | Steve Kosuth-Wood |