# OmniDrop – Project Plan & Development Roadmap

This document outlines the milestones, timeline, task dependencies, and Definition of Done (DoD) for the 5-member engineering team.

---

## 1. Development Phases & Milestones

```mermaid
gantt
    title OmniDrop Development Roadmap
    dateFormat  YYYY-MM-DD
    section Phase 1: Foundation
    Repository Initialization & CI Setup :done, p1_1, 2026-11-01, 3d
    Architecture & Protocol Specification :done, p1_2, 2026-11-04, 3d
    Base Scaffolding & Shared Interfaces :done, p1_3, 2026-11-07, 3d
    section Phase 2: Core Sockets
    Member 1: UDP Discovery Implementation :active, p2_1, 2026-11-10, 5d
    Member 2: TCP Sender & Framing :active, p2_2, 2026-11-10, 5d
    Member 3: TCP Receiver & File IO :active, p2_3, 2026-11-10, 5d
    section Phase 3: Advanced Network Features
    Member 4: One-to-Many Distribution :p3_1, 2026-11-15, 6d
    Member 5: Transfer Recovery & SHA256 :p3_2, 2026-11-15, 6d
    WebSocket Event Bridge & Frontend UI :p3_3, 2026-11-16, 5d
    section Phase 4: Integration & Testing
    Offline Multi-Device Testing :p4_1, 2026-11-22, 4d
    Security & Path Traversal Auditing :p4_2, 2026-11-24, 2d
    Oral Demo Preparation & Final Report :p4_3, 2026-11-26, 3d
```

---

## 2. Detailed Task Breakdown & Dependencies

### Phase 1: Environment & Architecture (Completed in Initialization)
- Monorepo structure, Git setup, GitHub CI workflows.
- Protocol specification: binary TCP framing and UDP datagram JSON structure.
- Compilable Java backend with Spring WebSocket and shared protocol classes.
- Responsive Next.js frontend with mobile-first dashboard and navigation.

### Phase 2: Core Socket Implementation (Days 1–5)
- **Member 1**: Bind `DatagramSocket(8888)`, start broadcast loop, implement inbound listener thread, filter self-beacons.
- **Member 2**: Implement `TcpFileSenderService`, binary frame encoder, chunk stream loop over `OutputStream`.
- **Member 3**: Implement `TcpFileReceiverService`, `ServerSocket(9090)`, binary frame decoder, disk writer with path sanitization.

### Phase 3: Advanced Network Capabilities (Days 6–11)
- **Member 4**: Integrate `ExecutorService` thread pool to parallelize TCP sender sockets to multiple target IP endpoints.
- **Member 5**: Implement chunk offset tracking in persistent checkpoint file (`.chk`), `RESUME_REQ` frame handling, and SHA-256 verification.
- **Full Team**: Connect socket progress listeners to Spring WebSocket notifications and ensure Next.js dashboard updates in real time.

### Phase 4: Verification, Security & Demonstration (Days 12–15)
- End-to-end testing on physical devices across an offline Wi-Fi hotspot.
- Stress testing with 500 MB+ files and rapid packet loss simulations.
- Final code freeze and presentation rehearsal.

---

## 3. Definition of Done (DoD)
A feature branch is ready for merge into `develop` only when:
1. All assigned Java classes adhere to standard socket programming principles.
2. Unit tests achieve >80% code coverage for the feature module.
3. Code compiles cleanly with zero Maven compiler warnings.
4. Path security and input sanitization are verified.
5. GitHub Actions CI pipeline passes with green status.
6. At least one peer review has approved the pull request.
