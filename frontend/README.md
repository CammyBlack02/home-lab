# Frontend (Dashboard UI)

Web dashboard that fetches from the backend API and displays server stats, devices, speed test, and (later) Govee controls.

**Next steps (Phase 1):**

- Single HTML page that calls the backend (e.g. `fetch('/api/server-stats')`) and renders cards/sections.
- Can live in `backend/src/main/resources/static` or here as a separate build; backend will serve it.

No build step required initially; plain HTML/CSS/JS is fine.
