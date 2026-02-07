# Agents (Python)

Lightweight Python services that collect system and (optionally) game/activity data. The Spring Boot backend polls them.

| Agent   | Runs on           | Purpose |
|--------|--------------------|---------|
| `server/` | Ubuntu server     | CPU, RAM, disk, uptime; optional security summary. |
| `desktop/`| Bazzite desktop   | System stats; optionally current game/activity. |

Each has its own README. Run as a systemd service or manually when developing.
