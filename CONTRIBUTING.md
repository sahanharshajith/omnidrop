# OmniDrop Contribution Guidelines

Welcome to the **OmniDrop** project! This guide explains how our 5-member engineering team contributes to the codebase, maintains code quality, and delivers network programming milestones collaboratively.

---

## 1. Code of Conduct

All contributors agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please maintain a respectful, constructive, and supportive environment.

---

## 2. Git Branching Strategy

We follow a structured Gitflow-inspired branching model:

- **`main`**: Production-ready, stable releases only. Protected branch.
- **`develop`**: Central integration branch for ongoing milestone development.
- **`feature/<concept-name>`**: Dedicated branches for individual member features.

### Assigned Feature Branches for Group Members:

| Member | Focus Area | Branch Name |
| :--- | :--- | :--- |
| **Member 1** | UDP Device Discovery | `feature/udp-discovery` |
| **Member 2** | TCP File Sender | `feature/tcp-sender` |
| **Member 3** | TCP File Receiver | `feature/tcp-receiver` |
| **Member 4** | One-to-Many Distribution | `feature/one-to-many` |
| **Member 5** | Transfer Recovery & Checkpoints | `feature/transfer-recovery` |

---

## 3. Commit Message Conventions

We strictly enforce [Conventional Commits](https://www.conventionalcommits.org/):

Format: `<type>(<scope>): <short description>`

### Allowed Types:
- `feat`: A new user-facing or networking feature
- `fix`: A bug fix
- `docs`: Documentation updates or protocol specification changes
- `test`: Adding or updating unit/integration tests
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `style`: Changes that do not affect code meaning (white-space, formatting)
- `chore`: Build process, dependencies, or tool updates

### Examples:
- `feat(udp): implement broadcast beacon with DatagramSocket`
- `feat(tcp): add binary framing and chunk streaming in TcpFileSender`
- `fix(recovery): handle offset positioning on resume request in FileChannel`
- `test(receiver): add unit test for path traversal prevention`
- `docs(protocol): define framing header bytes and opcode specification`

---

## 4. Pull Request (PR) Workflow

1. **Pull Latest Changes:**
   ```bash
   git checkout develop
   git pull origin develop
   ```
2. **Create / Rebase Feature Branch:**
   ```bash
   git checkout feature/udp-discovery
   git rebase develop
   ```
3. **Run Local Verification Before Pushing:**
   - Backend: `cd backend && mvn clean test`
   - Frontend: `cd frontend && npm run lint && npm run build`
4. **Push Branch & Open PR:**
   Push your branch to GitHub and create a Pull Request targeting `develop`.
5. **PR Review Rules:**
   - At least **1 peer review** is required before merging.
   - All CI checks (GitHub Actions) must pass.
   - Resolve all discussions before squash-and-merging.

---

## 5. Coding Standards

### Java Backend (Spring Boot 3.4 / Java 24)
- Adhere to Oracle Java Code Conventions.
- Use **Java standard networking APIs** (`java.net.Socket`, `java.net.ServerSocket`, `java.net.DatagramSocket`, `java.nio.channels.FileChannel`). Do NOT wrap third-party file transfer libraries.
- Validate every input parameter (prevent path traversal, validate byte sizes).
- Handle thread management through controlled `ExecutorService` instances; never spawn unbounded raw threads.
- Always close sockets and streams safely using `try-with-resources`.

### Next.js Frontend (Next.js 15+ / React 19 / TypeScript / Tailwind CSS)
- Strict TypeScript mode enabled; no `any` types unless strictly justified.
- Functional React components with standard React Hooks.
- Mobile-first responsive styling using Tailwind CSS utility classes.
- Accessible components with semantic HTML and ARIA labels.
