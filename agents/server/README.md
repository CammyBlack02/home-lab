# Server agent (Python)

Runs on the **Ubuntu server**. Exposes system stats (CPU, RAM, disk, uptime) over HTTP for the backend to poll.

**Next steps (Phase 2):**

- Small HTTP server (e.g. Flask or FastAPI) or script that writes JSON to a file the backend reads.
- Endpoint or file with: `cpu_percent`, `memory_percent`, `disk_usage`, `uptime`.
- Optional: security summary (e.g. fail2ban status, pending updates).

Requirements: Python 3.8+. Consider `psutil` for cross-platform stats.
