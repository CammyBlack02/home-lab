# Home monitoring hub – architecture diagrams

Reference diagrams for the home monitoring dashboard project. These can be rendered in any Markdown viewer that supports Mermaid (e.g. GitHub, Cursor, VS Code with a Mermaid extension).

---

## 1. High-level architecture

```mermaid
flowchart LR
  subgraph lan [Home LAN]
    Unifi[Unifi Controller]
    Server[Ubuntu Server]
    Desktop[Bazzite Desktop]
    Govee[Govee Lights]
    Backend[Backend API]
    Frontend[Web Dashboard]
  end
  subgraph clients [Clients]
    Stream7[HP Stream 7]
    Mac[MacBook]
    Phone[iPhone]
  end
  Unifi --> Backend
  Server --> Backend
  Desktop --> Backend
  Govee --> Backend
  Backend --> Frontend
  Frontend --> Stream7
  Frontend --> Mac
  Frontend --> Phone
```

---

## 2. Data flow

```mermaid
flowchart TB
  subgraph sources [Data sources]
    UnifiAPI[Unifi API]
    ServerAgent[Server Python agent]
    DesktopAgent[Desktop Python agent]
    SpeedTest[Speed test CLI]
    GoveeAPI[Govee API]
  end
  subgraph backend [Backend - Spring Boot]
    Aggregator[Aggregate and cache]
    REST[REST API]
    Aggregator --> REST
  end
  subgraph ui [Clients]
    Browser[Browser - Dashboard UI]
  end
  UnifiAPI --> Aggregator
  ServerAgent --> Aggregator
  DesktopAgent --> Aggregator
  SpeedTest --> Aggregator
  Aggregator --> GoveeAPI
  REST --> Browser
  Browser --> REST
```

---

## 3. Component view

```mermaid
flowchart LR
  subgraph backend [Backend - Ubuntu server]
    SpringBoot[Spring Boot app]
    Static[Static frontend]
  end
  subgraph agents [Agents]
    ServerPy[Server agent - Python]
    DesktopPy[Desktop agent - Python]
  end
  subgraph external [External]
    Unifi[Unifi Controller]
    Govee[Govee cloud]
  end
  SpringBoot --> Unifi
  SpringBoot --> Govee
  SpringBoot --> ServerPy
  SpringBoot --> DesktopPy
  SpringBoot --> Static
```

---

## 4. Access and deployment

```mermaid
flowchart TB
  subgraph internet [Internet]
    MacBook[MacBook]
    iPhone[iPhone]
  end
  subgraph vpn [VPN into home]
    VPN[Unifi VPN]
  end
  subgraph lan [Home LAN]
    Stream7[HP Stream 7 - Kiosk]
    Server[Ubuntu server]
    Dashboard[Backend and dashboard]
  end
  MacBook --> VPN
  iPhone --> VPN
  VPN --> Dashboard
  Stream7 --> Dashboard
  Server --> Dashboard
```

---

## 5. Phase roadmap

```mermaid
flowchart LR
  subgraph p1 [Phase 1]
    P1A[Spring Boot backend]
    P1B[Mock or simple data]
    P1C[Basic web UI]
    P1D[Stream 7 kiosk]
  end
  subgraph p2 [Phase 2]
    P2A[Unifi integration]
    P2B[Server agent]
    P2C[Desktop agent]
  end
  subgraph p3 [Phase 3]
    P3A[Speed test]
    P3B[Govee control]
    P3C[Responsive UI]
  end
  subgraph p4 [Phase 4]
    P4A[Game detection]
    P4B[Auth and alerts]
  end
  P1A --> P2A
  P1C --> P2A
  P2C --> P3A
  P3C --> P4A
```

---

## 6. Tech stack overview

```mermaid
flowchart TB
  subgraph frontend [Frontend]
    UI[HTML/CSS/JS or Vue/React]
  end
  subgraph backend [Backend]
    Java[Spring Boot - Java]
  end
  subgraph agents [Agents]
    PyServer[Python - Server agent]
    PyDesktop[Python - Desktop agent]
  end
  subgraph external [External APIs]
    Unifi[Unifi API]
    Govee[Govee Open API]
  end
  UI --> Java
  Java --> PyServer
  Java --> PyDesktop
  Java --> Unifi
  Java --> Govee
```
