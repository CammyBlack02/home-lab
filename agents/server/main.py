"""
Server agent – runs on Ubuntu server.
Exposes real system stats over HTTP for the backend to poll.
"""
import time
from flask import Flask, jsonify

try:
    import psutil
except ImportError:
    psutil = None

app = Flask(__name__)


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

    return jsonify({
        "hostname": psutil.os.uname().nodename if hasattr(psutil.os, "uname") else "ubuntu-server",
        "uptime_seconds": _get_uptime_seconds(),
        "cpu_percent": round(cpu, 1) if cpu is not None else None,
        "memory_percent": memory_percent,
        "disk_used_percent": _get_disk_usage(),
        "timestamp": int(time.time() * 1000),
    })


@app.route("/health")
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
