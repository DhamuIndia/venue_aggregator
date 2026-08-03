# Git Branching Workflow

VenueMart uses two long-lived branches:

- `main` is the production branch.
- `develop` is the pre-production integration branch.

Feature and ordinary fix branches must start from `develop` and return to
`develop` through a pull request. Production releases move only from `develop`
to `main` through a separate pull request.

```text
feature/* or fix/* -> develop -> main
```

## Start feature work

Always refresh `develop` before creating a branch:

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/<short-name>
```

Codex-owned branches may use `codex/<short-name>` instead of `feature/*`, but
they must still be created from `develop`.

## Merge feature work

1. Push the feature branch.
2. Open a pull request with `develop` as the base branch.
3. Run backend and frontend checks relevant to the change.
4. Review and verify the change in the pre-production environment.
5. Merge the pull request into `develop`.

Never merge an ordinary feature branch directly into `main`.

## Release to production

1. Confirm the tested pre-production commit on `develop`.
2. Open a release pull request from `develop` to `main`.
3. Review migrations, configuration changes, rollback steps, and data-safety
   impact.
4. Merge with a merge commit so branch ancestry remains visible.
5. Deploy with `BRANCH=main` only after explicit production approval.
6. Verify production health and record the deployed commit.
7. Merge `main` back into `develop` when the release merge adds a commit that is
   not already present there.

Merging a pull request into `main` does not itself authorize a deployment.

## Emergency hotfixes

Only urgent production fixes may start from `main`:

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c hotfix/<short-name>
```

After the hotfix is reviewed and merged into `main`, merge `main` back into
`develop` immediately so the fix is not lost from future releases.

## Protection rules

Protect both long-lived branches:

- require pull requests before merging;
- require relevant automated checks;
- require resolved review conversations;
- prevent force pushes and branch deletion;
- allow direct pushes only for an explicitly approved emergency; and
- require production approval before deploying `main`.

The Git branch named `develop` is the source for pre-production deployments.
Creating the branch does not create or modify pre-production infrastructure.
