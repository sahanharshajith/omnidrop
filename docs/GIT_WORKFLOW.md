# OmniDrop – Git Collaboration & Contribution Workflow

This document provides exact step-by-step instructions for members to manage branches, write conventional commits, review pull requests, and resolve merge conflicts.

---

## 1. Branch Strategy & Rules

```
main (Protected: Stable production releases only)
  ▲
  │ Pull Request (Sprint Milestones)
develop (Default: Integration branch)
  ▲
  ├───────────────────────┬───────────────────────┬───────────────────────┐
  │                       │                       │                       │
feature/udp-discovery  feature/tcp-sender   feature/tcp-receiver   feature/one-to-many ...
(Member 1)             (Member 2)           (Member 3)             (Member 4)
```

- **Direct commits to `main` and `develop` are prohibited.**
- Every member works exclusively in their designated `feature/<concept>` branch.
- Feature branches branch off `develop` and merge back into `develop` through GitHub Pull Requests.

---

## 2. Step-by-Step Daily Workflow

### Step 1: Sync with Latest `develop`
```bash
git checkout develop
git pull origin develop
```

### Step 2: Switch to Your Feature Branch
```bash
git checkout feature/udp-discovery
git rebase develop
```

### Step 3: Implement, Test & Stage Changes
```bash
# Verify backend compiles and tests pass before committing
cd backend
mvn clean test

# Stage your modified files
git add src/main/java/com/omnidrop/discovery/UdpDiscoveryService.java
```

### Step 4: Write a Conventional Commit
```bash
git commit -m "feat(discovery): implement UDP beacon listener with DatagramPacket"
```

### Step 5: Push Branch and Open a Pull Request
```bash
git push -u origin feature/udp-discovery
```
Open a PR on GitHub targeting the `develop` branch. Fill out the PR template completely.

---

## 3. Resolving Merge Conflicts

If another member merged code that conflicts with your branch:

1. Fetch and rebase against `develop`:
   ```bash
   git fetch origin
   git rebase origin/develop
   ```
2. Git will pause on conflicting files. Open the files in VS Code / IntelliJ IDEA and choose the correct code combination.
3. Mark conflicts resolved and continue rebase:
   ```bash
   git add <resolved-file>
   git rebase --continue
   ```
4. Force push your updated branch:
   ```bash
   git push --force-with-lease origin feature/udp-discovery
   ```

---

## 4. Code Review Guidelines
- Verify that **no hardcoded file paths** or **arbitrary command executions** exist.
- Ensure all socket handles and stream channels are closed via `try-with-resources`.
- Verify that tests exist and pass locally.
