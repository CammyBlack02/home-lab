# Desktop agent (Python)

Runs on the **Bazzite desktop**. Exposes system stats (CPU, RAM, GPU if present) and optionally current game/activity.

**Next steps (Phase 2):**

- Small HTTP server or script exposing: CPU, memory, GPU (e.g. via `nvidia-smi` or similar).
- Optional (Phase 4): game detection – e.g. Steam “now playing”, Discord Rich Presence, or foreground process name.

Requirements: Python 3.8+. Consider `psutil`; for GPU, parse `nvidia-smi` or use a small library.
