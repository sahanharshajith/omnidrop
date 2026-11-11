# OmniDrop – Development Environment Setup Guide

This guide covers installing prerequisites, building the project, running the services, and setting up an offline multi-device test environment.

---

## 1. System Prerequisites

| Tool | Minimum Version | Recommended Version | Verification Command |
| :--- | :--- | :--- | :--- |
| **Java JDK** | 21 LTS | 24 LTS | `java -version` |
| **Apache Maven** | 3.8.x | 3.9.x+ | `mvn -version` |
| **Node.js** | 20.x LTS | 22.x LTS | `node -v` |
| **npm** | 10.x+ | 11.x+ | `npm -v` |
| **Git** | 2.30+ | Latest | `git --version` |
| **Docker** (Optional) | 24.x+ | Latest | `docker --version` |

---

## 2. Quick Setup & Build

### 2.1 Clone Repository
```bash
git clone https://github.com/<org>/omnidrop.git
cd omnidrop
```

### 2.2 Backend Build (Spring Boot / Java)
```bash
# Navigate to backend directory
cd backend

# Compile all Java sources
mvn clean compile

# Run automated tests
mvn test

# Package executable JAR
mvn package -DskipTests
```

### 2.3 Frontend Setup (Next.js)
```bash
# Navigate to frontend directory
cd ../frontend

# Install dependencies
npm install

# Run type-check and linter
npm run lint

# Build production bundle
npm run build
```

---

## 3. Running OmniDrop Locally (Development Mode)

### Windows PowerShell:
```powershell
# In Terminal 1 (Backend Agent):
cd backend
mvn spring-boot:run

# In Terminal 2 (Next.js Frontend):
cd frontend
npm run dev
```

### Linux / macOS:
```bash
# In Terminal 1 (Backend Agent):
cd backend && ./mvnw spring-boot:run

# In Terminal 2 (Next.js Frontend):
cd frontend && npm run dev
```

The application will be accessible at:
- Frontend UI: `http://localhost:3000`
- Backend REST API: `http://localhost:8080/api/v1/health`
- Backend WebSocket: `ws://localhost:8080/ws/transfers`

---

## 4. Dockerized Setup

To launch both backend and frontend via Docker Compose:
```bash
docker compose up --build
```

---

## 5. Offline Multi-Device Network Setup (Wi-Fi / Hotspot)

To test OmniDrop with two or more physical devices without an internet connection:

```
+-------------------------------------------------------------+
|                Local Wi-Fi Router / Hotspot                 |
|               (No Internet Uplink Required)                 |
+------------------------------+------------------------------+
                               |
               +---------------+---------------+
               |                               |
       [Device A: Laptop]              [Device B: Laptop/Phone]
       IP: 192.168.1.100               IP: 192.168.1.101
       Runs: Java Backend + Web        Runs: Java Backend + Web
       UDP Port: 8888                  UDP Port: 8888
       TCP Port: 9090                  TCP Port: 9090
```

### Step 1: Connect to the Same Network
Connect all participating devices to the same local Wi-Fi router or activate a mobile hotspot on a phone and connect the laptops to it. **Internet access is not required.**

### Step 2: Configure Firewall Exceptions
Both Windows Defender Firewall and macOS Application Firewall may block incoming UDP broadcasts or TCP server socket binds.

**Windows PowerShell (Run as Administrator):**
```powershell
New-NetFirewallRule -DisplayName "OmniDrop TCP" -Direction Inbound -LocalPort 9090 -Protocol TCP -Action Allow
New-NetFirewallRule -DisplayName "OmniDrop UDP" -Direction Inbound -LocalPort 8888 -Protocol UDP -Action Allow
New-NetFirewallRule -DisplayName "OmniDrop Web" -Direction Inbound -LocalPort 8080,3000 -Protocol TCP -Action Allow
```

**macOS / Linux:**
Allow port `8888` (UDP) and ports `9090`, `8080`, `3000` (TCP) in `ufw` or macOS Security Preferences.

### Step 3: Run the Application on Each Node
Start the Java backend and web interface on each device. The UDP discovery module will automatically detect the other nodes within 3 seconds, showing their IP addresses on the Discovery page.
