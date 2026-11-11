## Description
Briefly describe the purpose of this PR and which network programming concept or module it addresses.

## Concept / Member Mapping
- [ ] Member 1: UDP Device Discovery (`DatagramSocket`, `DatagramPacket`)
- [ ] Member 2: TCP File Sender (`Socket`, Streaming chunks)
- [ ] Member 3: TCP File Receiver (`ServerSocket`, File writing & validation)
- [ ] Member 4: One-to-Many Distribution (`ExecutorService`, Concurrent TCP)
- [ ] Member 5: Transfer Recovery (`FileChannel`, Checkpoints, SHA-256)
- [ ] Cross-Cutting: Frontend UI / WebSocket / Documentation

## Changes Proposed
- Summary of technical changes
- Java classes added/modified
- Frontend components added/modified

## Verification & Testing
- [ ] `mvn clean test` passed locally with zero errors
- [ ] `npm run lint` & `npm run build` passed locally
- [ ] Verified offline without internet connection
- [ ] Tested on local loopback or real local network

## Checklist
- [ ] My code adheres to the project coding standards and conventions
- [ ] I have included unit tests covering my changes
- [ ] I have updated relevant documentation in `/docs`
- [ ] No hardcoded file paths or insecure path traversal vulnerabilities
