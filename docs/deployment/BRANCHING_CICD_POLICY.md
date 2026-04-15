# NewsAnalyzer - Branching & CI/CD Policy

**Last Updated:** 2025-12-16
**Strategy:** GitHub Flow with Release Tags

---

## Overview

NewsAnalyzer uses **GitHub Flow** - a lightweight, branch-based workflow optimized for continuous deployment. All changes go through feature branches and pull requests before merging to `master`.

---

## Branching Strategy

### Branch Types

| Branch Pattern | Purpose | Lifetime | CI Runs |
|---------------|---------|----------|---------|
| `master` | Production-ready code | Permanent | Yes |
| `feature/*` | New features | Short-lived | Yes |
| `fix/*` | Bug fixes | Short-lived | Yes |
| `hotfix/*` | Critical production fixes | Short-lived | Yes |

### Branch Naming Convention

```
feature/STORY-123-short-description
fix/STORY-456-bug-description
hotfix/critical-security-patch
```

**Examples:**
- `feature/STORY-101-ssl-certificates`
- `feature/STORY-102-entity-linking`
- `fix/STORY-103-migration-error`
- `hotfix/database-connection-leak`

---

## Workflow

### 1. Creating a Feature Branch

```bash
# Start from latest master
git checkout master
git pull origin master

# Create feature branch
git checkout -b feature/STORY-123-add-authentication

# Work on your changes...
git add .
git commit -m "Add JWT authentication middleware"
```

### 2. Pushing and Creating PR

```bash
# Push feature branch
git push -u origin feature/STORY-123-add-authentication

# Create PR via GitHub CLI or web UI
gh pr create --title "STORY-123: Add authentication" \
  --body "Implements JWT authentication for API endpoints"
```

### 3. PR Review Process

1. **Automated checks run:**
   - API Integration Tests
   - Code quality checks
   - Security scans (if configured)

2. **Manual review required:**
   - Code review by team member
   - Approval required before merge

3. **Merge to master:**
   - Squash and merge (keeps history clean)
   - Delete feature branch after merge

### 4. Creating a Release

```bash
# After sprint completion, tag the release
git checkout master
git pull origin master

# Create annotated tag
git tag -a v2.1.0 -m "Sprint 1: Authentication and SSL"
git push origin v2.1.0
```

This triggers:
- Full test suite
- Production deployment
- GitHub Release creation

---

## CI/CD Pipelines

### Pipeline Overview

```
                    ┌─────────────────────────────────────────────┐
                    │              GitHub Actions                  │
                    └─────────────────────────────────────────────┘
                                        │
            ┌───────────────────────────┼───────────────────────────┐
            │                           │                           │
    ┌───────▼───────┐          ┌───────▼───────┐          ┌───────▼───────┐
    │   On Push to   │          │    On PR to   │          │   On Tag      │
    │ feature/fix/*  │          │    master     │          │    v*         │
    └───────┬───────┘          └───────┬───────┘          └───────┬───────┘
            │                           │                           │
    ┌───────▼───────┐          ┌───────▼───────┐          ┌───────▼───────┐
    │  Run Tests    │          │  Run Tests    │          │  Run Tests    │
    │  (Fast FB)    │          │  (Gate PR)    │          │  (Pre-Deploy) │
    └───────────────┘          └───────────────┘          └───────┬───────┘
                                                                   │
                                                          ┌───────▼───────┐
                                                          │   Deploy to   │
                                                          │  Production   │
                                                          └───────┬───────┘
                                                                   │
                                                          ┌───────▼───────┐
                                                          │    Create     │
                                                          │   Release     │
                                                          └───────────────┘
```

### Workflow Files

| File | Trigger | Purpose |
|------|---------|---------|
| `api-tests.yml` | Push to any branch, PRs | Run integration tests |
| `deploy-production.yml` | Tags `v*`, manual | Deploy to production |

---

## Branch Protection Rules

### Recommended Settings for `master`

Configure these in GitHub: **Settings > Branches > Branch protection rules**

| Setting | Value | Reason |
|---------|-------|--------|
| **Require pull request before merging** | Yes | No direct pushes |
| **Require approvals** | 1 | Code review required |
| **Dismiss stale reviews** | Yes | Re-review after changes |
| **Require status checks** | Yes | Tests must pass |
| **Required checks** | `api-tests` | Integration tests |
| **Require branches up to date** | Yes | Prevent merge conflicts |
| **Require conversation resolution** | Yes | Address all comments |
| **Restrict who can push** | Admins only | Emergency fixes only |
| **Allow force pushes** | No | Protect history |
| **Allow deletions** | No | Protect branch |

### How to Configure

```bash
# Via GitHub CLI
gh api repos/noometric-llc/news-analyzer/branches/master/protection \
  --method PUT \
  --field required_status_checks='{"strict":true,"contexts":["api-tests"]}' \
  --field enforce_admins=false \
  --field required_pull_request_reviews='{"required_approving_review_count":1}' \
  --field restrictions=null
```

Or via GitHub Web UI:
1. Go to **Settings** > **Branches**
2. Click **Add rule**
3. Enter `master` as branch name pattern
4. Configure settings as above

---

## Release Versioning

### Semantic Versioning

```
v{MAJOR}.{MINOR}.{PATCH}

MAJOR: Breaking changes
MINOR: New features (backward compatible)
PATCH: Bug fixes (backward compatible)
```

**Examples:**
- `v2.0.0` - Initial production release
- `v2.1.0` - Sprint 1 features (SSL, authentication)
- `v2.1.1` - Bug fix release
- `v3.0.0` - Major architecture change

### Release Cadence

| Type | Frequency | Tag Example |
|------|-----------|-------------|
| Sprint Release | Every 2 weeks | `v2.1.0`, `v2.2.0` |
| Hotfix | As needed | `v2.1.1`, `v2.1.2` |
| Major | Quarterly | `v3.0.0` |

---

## GitHub Secrets Required

For the deploy workflow to function, add these secrets in GitHub:

**Settings > Secrets and variables > Actions**

| Secret | Description |
|--------|-------------|
| `DEPLOY_SSH_KEY` | Private SSH key for Hetzner server |

### Adding the Deploy Key

```bash
# Copy your deploy key content
cat ~/.ssh/id_ed25519_deploy

# Add to GitHub:
# 1. Go to Settings > Secrets and variables > Actions
# 2. Click "New repository secret"
# 3. Name: DEPLOY_SSH_KEY
# 4. Value: (paste the private key content)
```

---

## Quick Reference

### Daily Development

```bash
# Start new feature
git checkout master && git pull
git checkout -b feature/STORY-XXX-description

# Commit changes
git add . && git commit -m "Description"
git push -u origin feature/STORY-XXX-description

# Create PR
gh pr create
```

### Sprint Release

```bash
# After all PRs merged
git checkout master && git pull
git tag -a v2.X.0 -m "Sprint X release"
git push origin v2.X.0
```

### Hotfix

```bash
# Critical fix
git checkout master && git pull
git checkout -b hotfix/critical-description

# Fix, commit, PR, merge, then:
git checkout master && git pull
git tag -a v2.X.1 -m "Hotfix: description"
git push origin v2.X.1
```

---

## Environment Protection

### Production Environment

Configure in GitHub: **Settings > Environments > production**

| Setting | Value |
|---------|-------|
| **Required reviewers** | 1 (optional) |
| **Wait timer** | 0 minutes |
| **Deployment branches** | Tags only (`v*`) |

This ensures only tagged releases can deploy to production.

---

## Monitoring Deployments

### View Deployment Status

```bash
# List recent deployments
gh run list --workflow=deploy-production.yml

# View specific run
gh run view <run-id>
```

### Rollback Procedure

```bash
# If deployment fails, redeploy previous version
git tag -a v2.X.0-rollback -m "Rollback to v2.X-1.0"
git push origin v2.X.0-rollback

# Or manually on server
ssh -i ~/.ssh/id_ed25519_deploy root@5.78.71.195
cd /opt/newsanalyzer
git checkout v2.X-1.0
docker compose -f docker-compose.deploy.yml up -d
```

---

## Version History

| Date | Change |
|------|--------|
| 2025-12-16 | Initial policy document |
