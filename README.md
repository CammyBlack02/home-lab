# Home Lab

Central hub for home monitoring and control: dashboard (server/desktop stats, network devices, internet speed, Govee lights), viewable on an HP Stream 7 kiosk and anywhere via VPN.

## Structure

| Folder      | Description |
|------------|-------------|
| `backend/` | Spring Boot API – aggregates Unifi, agents, speed test, Govee; serves frontend. |
| `frontend/`| Web dashboard UI – HTML/JS or Vue/React, consumed by backend. |
| `agents/`  | Python agents: `server/` (Ubuntu server stats), `desktop/` (Bazzite desktop stats + optional game). |
| `docs/`    | Architecture diagrams and project notes. |

## Tech

- **Backend**: Java, Spring Boot
- **Frontend**: HTML/CSS/JS (or Vue/React later)
- **Agents**: Python
- **External**: Unifi API, Govee Open API

## Quick start

1. **Backend**: From `backend/`, run the Spring Boot app (see `backend/README.md` once set up).
2. **Frontend**: Served by the backend or open `frontend/index.html` against a local API.
3. **Agents**: Run server agent on Ubuntu server, desktop agent on Bazzite (see `agents/*/README.md`).
4. **Access**: Dashboard at `http://<server>:port` on LAN or via VPN.

See `docs/architecture-diagrams.md` for diagrams and the project plan (in Cursor) for the full roadmap and to-do list.
