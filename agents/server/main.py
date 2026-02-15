"""
Server agent – runs on Ubuntu server.
Exposes real system stats and security monitoring over HTTP for the backend to poll.
"""
import re
import subprocess
import time
from flask import Flask, jsonify

try:
    import psutil
except ImportError:
    psutil = None

app = Flask(__name__)


def _security_failed_ssh_24h():
    """Count failed SSH login attempts in the last 24 hours (journalctl or auth.log)."""
    try:
        r = subprocess.run(
            ["journalctl", "-u", "ssh", "--since", "24 hours ago", "--no-pager", "-o", "cat"],
            capture_output=True, text=True, timeout=5,
        )
        if r.returncode == 0 and r.stdout:
            return r.stdout.count("Failed password") + r.stdout.count("Failed publickey")
        # Fallback: sshd unit name on some systems
        r = subprocess.run(
            ["journalctl", "-u", "sshd", "--since", "24 hours ago", "--no-pager", "-o", "cat"],
            capture_output=True, text=True, timeout=5,
        )
        if r.returncode == 0 and r.stdout:
            return r.stdout.count("Failed password") + r.stdout.count("Failed publickey")
    except (FileNotFoundError, subprocess.TimeoutExpired, PermissionError):
        pass
    return None


def _security_listening_ports():
    """List TCP ports in LISTEN state with process name (best-effort)."""
    if not psutil:
        return None
    try:
        conns = psutil.net_connections(kind="inet")
        seen = set()
        out = []
        for c in conns:
            if c.status != "LISTEN" or c.laddr is None:
                continue
            port = c.laddr.port
            if port in seen:
                continue
            seen.add(port)
            name = "—"
            try:
                if c.pid:
                    name = psutil.Process(c.pid).name()
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                pass
            out.append({"port": port, "process": name})
        out.sort(key=lambda x: x["port"])
        return out[:50]  # cap for UI
    except (psutil.AccessDenied, Exception):
        return None


def _security_ufw_status():
    """UFW status: active, inactive, or error/unknown."""
    try:
        r = subprocess.run(
            ["ufw", "status"],
            capture_output=True, text=True, timeout=5,
        )
        if r.returncode == 0 and r.stdout:
            if "active" in r.stdout.lower().split("\n")[0]:
                return "active"
            if "inactive" in r.stdout.lower():
                return "inactive"
    except (FileNotFoundError, subprocess.TimeoutExpired, PermissionError):
        pass
    return None


def _security_updates_pending():
    """Count of upgradable packages (apt); security count if detectable."""
    try:
        r = subprocess.run(
            ["apt-get", "-s", "upgrade", "-o", "Dir::Cache::archives="],
            capture_output=True, text=True, timeout=15,
        )
        if r.returncode == 0 and r.stdout:
            lines = [l for l in r.stdout.splitlines() if l.startswith("Inst ")]
            total = len(lines)
            security = sum(1 for l in lines if "security" in l.lower() or "-security" in l)
            return {"total": total, "security": security}
    except (FileNotFoundError, subprocess.TimeoutExpired, PermissionError):
        pass
    return None


def _security_fail2ban():
    """Fail2ban status and per-jail stats (currently banned, total banned, currently failed)."""
    try:
        r = subprocess.run(
            ["fail2ban-client", "status"],
            capture_output=True, text=True, timeout=5,
        )
        if r.returncode != 0 or not r.stdout:
            return None
        # Parse "Jail list: nginx-http-auth, sshd" or "Jail list:	nginx-http-auth, sshd"
        jail_list_match = re.search(r"Jail list:\s*([^\n]+)", r.stdout)
        if not jail_list_match:
            return None
        jail_names = [s.strip() for s in jail_list_match.group(1).split(",") if s.strip()]
        jails = []
        for name in jail_names:
            rj = subprocess.run(
                ["fail2ban-client", "status", name],
                capture_output=True, text=True, timeout=5,
            )
            if rj.returncode != 0 or not rj.stdout:
                jails.append({"name": name, "currently_banned": None, "total_banned": None, "currently_failed": None})
                continue
            cb = re.search(r"Currently banned:\s*(\d+)", rj.stdout)
            tb = re.search(r"Total banned:\s*(\d+)", rj.stdout)
            cf = re.search(r"Currently failed:\s*(\d+)", rj.stdout)
            jails.append({
                "name": name,
                "currently_banned": int(cb.group(1)) if cb else None,
                "total_banned": int(tb.group(1)) if tb else None,
                "currently_failed": int(cf.group(1)) if cf else None,
            })
        return {"status": "active", "jails": jails}
    except (FileNotFoundError, subprocess.TimeoutExpired, PermissionError):
        return None


def _security_unattended_upgrades():
    """Unattended-upgrades: installed and optionally last run from log."""
    try:
        r = subprocess.run(
            ["dpkg", "-l", "unattended-upgrades"],
            capture_output=True, text=True, timeout=5,
        )
        if r.returncode != 0 or "unattended-upgrades" not in (r.stdout or ""):
            return None
        out = {"installed": True}
        # Optional: last run from log
        r2 = subprocess.run(
            ["sh", "-c", "ls -t /var/log/unattended-upgrades/unattended-upgrades*.log 2>/dev/null | head -1"],
            capture_output=True, text=True, timeout=2,
        )
        if r2.returncode == 0 and r2.stdout.strip():
            import os
            path = r2.stdout.strip()
            try:
                mtime = os.path.getmtime(path)
                out["last_run_ts"] = int(mtime)
            except OSError:
                pass
        return out
    except (FileNotFoundError, subprocess.TimeoutExpired, PermissionError):
        return None


def _security_apparmor():
    """AppArmor: loaded and enforce count from aa-status."""
    try:
        r = subprocess.run(
            ["aa-status"],
            capture_output=True, text=True, timeout=5,
        )
        if r.returncode != 0 or not r.stdout:
            return None
        out = {"loaded": "apparmor module is loaded" in r.stdout or "profiles are loaded" in r.stdout}
        enforced = re.search(r"(\d+)\s+profiles are in enforce mode", r.stdout)
        loaded_n = re.search(r"(\d+)\s+profiles are loaded", r.stdout)
        if enforced:
            out["profiles_enforce"] = int(enforced.group(1))
        if loaded_n:
            out["profiles_loaded"] = int(loaded_n.group(1))
        return out if out else None
    except (FileNotFoundError, subprocess.TimeoutExpired, PermissionError):
        return None


def _get_disk_usage():
    try:
        d = psutil.disk_usage("/")
        return round(d.percent, 1)
    except Exception:
        return None


def _get_uptime_seconds():
    try:
        return int(time.time() - psutil.boot_time())
    except Exception:
        return None


@app.route("/stats")
def stats():
    if not psutil:
        return jsonify({
            "hostname": "ubuntu-server",
            "uptime_seconds": 0,
            "cpu_percent": 0,
            "memory_percent": 0,
            "disk_used_percent": 0,
            "timestamp": int(time.time() * 1000),
            "error": "psutil not installed",
        }), 200

    try:
        cpu = psutil.cpu_percent(interval=0.1)
    except Exception:
        cpu = None
    try:
        mem = psutil.virtual_memory()
        memory_percent = round(mem.percent, 1)
    except Exception:
        memory_percent = None

    payload = {
        "hostname": psutil.os.uname().nodename if hasattr(psutil.os, "uname") else "ubuntu-server",
        "uptime_seconds": _get_uptime_seconds(),
        "cpu_percent": round(cpu, 1) if cpu is not None else None,
        "memory_percent": memory_percent,
        "disk_used_percent": _get_disk_usage(),
        "timestamp": int(time.time() * 1000),
    }
    # Security monitoring (best-effort; may be None if no access)
    failed_ssh = _security_failed_ssh_24h()
    listening = _security_listening_ports()
    ufw = _security_ufw_status()
    updates = _security_updates_pending()
    fail2ban = _security_fail2ban()
    unattended = _security_unattended_upgrades()
    apparmor = _security_apparmor()
    security = {}
    if failed_ssh is not None:
        security["failed_ssh_logins_24h"] = failed_ssh
    if listening is not None:
        security["listening_ports"] = listening
    if ufw is not None:
        security["ufw_status"] = ufw
    if updates is not None:
        security["updates_pending"] = updates.get("total")
        security["security_updates_pending"] = updates.get("security")
    if fail2ban is not None:
        security["fail2ban"] = fail2ban
    if unattended is not None:
        security["unattended_upgrades"] = unattended
    if apparmor is not None:
        security["apparmor"] = apparmor
    if security:
        payload["security"] = security
    return jsonify(payload)


@app.route("/health")
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
