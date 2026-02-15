# Server agent (Python)

Runs on the **Ubuntu server**. Exposes system stats and security monitoring over HTTP for the backend to poll.

**Endpoints**

- `GET /stats` – hostname, CPU, memory, disk, uptime, and optional **security** block (see below).
- `GET /health` – health check.

**Security block** (best-effort; shown in the dashboard Server card expanded view)

- **Failed SSH logins (24h)** – from `journalctl -u ssh` (or `sshd`).
- **Listening ports** – TCP ports in LISTEN state with process name (from psutil).
- **UFW status** – `active` or `inactive` from `ufw status`.
- **Fail2ban** – status and per-jail stats (currently banned, total banned, currently failed) from `fail2ban-client status` (usually needs root).
- **Unattended-upgrades** – installed and last run time from log (if readable).
- **AppArmor** – loaded and profile counts from `aa-status` (usually needs root).
- **Updates pending** – total and security count from `apt-get -s upgrade` (may require root to run apt).

If the agent cannot read a source (e.g. no journalctl access, or fail2ban/aa-status without root), that field is omitted; the rest still appear. Run the agent as root (e.g. systemd service) to get fail2ban and AppArmor stats.

**Requirements:** Python 3.8+, `psutil`. Run: `pip install flask psutil`.
