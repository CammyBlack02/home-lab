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

## Quick start (Phase 1 – mock data)

1. **Backend + dashboard:** From `backend/`, run `mvn spring-boot:run`. Open http://localhost:8080/ for the dashboard.
2. **Agents (optional):** Run server agent: `cd agents/server && pip install -r requirements.txt && python main.py` (port 5000). Desktop: `cd agents/desktop && pip install -r requirements.txt && python main.py` (port 5001). Backend still uses mock data until you wire it in Phase 2.
3. **Deploy:** Build JAR with `mvn -f backend clean package`, copy to Ubuntu server, run with `java -jar ...`. Access at `http://<server>:8080` on LAN or VPN.
4. **Secrets:** Copy `.env.example` to `.env` and fill in when you add Govee/Unifi (do not commit `.env`).

**Optional (Phase 4):** PS5 “last played” – show last played game via PSN API (e.g. [psn-api](https://github.com/achievements-app/psn-api)); no real-time “currently playing,” use “recently played” + refresh token.

See `docs/architecture-diagrams.md` for diagrams and the project plan (in Cursor) for the full roadmap and to-do list.
