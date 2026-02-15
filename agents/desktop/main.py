"""
Desktop agent – runs on Bazzite desktop.
Exposes real system stats (and optionally GPU) over HTTP for the backend to poll.
"""
import subprocess
import time
from flask import Flask, jsonify

try:
    import psutil
except ImportError:
    psutil = None

app = Flask(__name__)


def _get_gpu_util():
    """Try nvidia-smi, then rocm-smi (AMD), for GPU utilisation; return None if not available."""
    # NVIDIA
    try:
        out = subprocess.run(
            ["nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        if out.returncode == 0 and out.stdout.strip():
            return int(out.stdout.strip().split("\n")[0].strip())
    except (FileNotFoundError, subprocess.TimeoutExpired, ValueError):
        pass
    # AMD (ROCm) – e.g. rocm-smi --showuse prints GPU use %
    try:
        out = subprocess.run(
            ["rocm-smi", "--showuse"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        if out.returncode == 0 and out.stdout:
            for line in out.stdout.splitlines():
                if "GPU use" in line or "GPU Use" in line:
                    # e.g. "GPU use (%): 5" or "GPU Use (%): 12"
                    parts = line.split(":")
                    if len(parts) >= 2:
                        pct = parts[-1].strip().rstrip("%")
                        return int(float(pct))
    except (FileNotFoundError, subprocess.TimeoutExpired, ValueError):
        pass
    return None


def _get_current_activity():
    """Return name of the process using the most CPU (simple stand-in for 'current activity')."""
    if not psutil:
        return None
    try:
        our_pid = psutil.Process().pid
        best_name, best_cpu = None, 0.0
        for p in psutil.process_iter(["name", "cpu_percent"]):
            try:
                if p.pid == our_pid:
                    continue
                name = p.info.get("name") or ""
                cpu = p.info.get("cpu_percent") or 0
                if cpu > best_cpu and name:
                    best_cpu = cpu
                    best_name = name
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
        return best_name if best_name and best_cpu > 0.5 else None
    except Exception:
        return None


@app.route("/stats")
def stats():
    if not psutil:
        return jsonify({
            "hostname": "bazzite-desktop",
            "cpu_percent": 0,
            "memory_percent": 0,
            "gpu_util_percent": None,
            "current_activity": None,
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

    hostname = psutil.os.uname().nodename if hasattr(psutil.os, "uname") else "bazzite-desktop"

    return jsonify({
        "hostname": hostname,
        "cpu_percent": round(cpu, 1) if cpu is not None else None,
        "memory_percent": memory_percent,
        "gpu_util_percent": _get_gpu_util(),
        "current_activity": _get_current_activity(),
        "timestamp": int(time.time() * 1000),
    })


@app.route("/health")
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=False)
