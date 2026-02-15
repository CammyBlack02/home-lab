# Backend (Spring Boot)

REST API for the home monitoring dashboard. Phase 2: UniFi devices, server/desktop agents, speed test. Phase 3: Govee devices (lights, plugs, appliances).

## Run locally

**Requirements:** Java 17+, Maven 3.6+

```bash
cd backend
mvn spring-boot:run
```

If you have the Maven wrapper: `./mvnw spring-boot:run`.  
To generate the wrapper (one-time): `mvn -N wrapper:wrapper`.

- **API:** http://localhost:8080/api/server-stats, /api/desktop-stats, /api/devices, /api/speed-test, /api/govee-devices  
- **Dashboard:** http://localhost:8080/

## Phase 2 – real data (optional config)

Create `src/main/resources/application-local.yml` (do not commit) or set env vars:

**Agent URLs** (backend polls these; leave unset for mock):
- `homelab.server-agent-url`: e.g. `http://192.168.1.x:5000` (Ubuntu server agent)
- `homelab.desktop-agent-url`: e.g. `http://192.168.1.y:5001` (Bazzite desktop agent)

**Unifi** (real device list; leave disabled for mock):
- `homelab.unifi.enabled`: `true`
- `homelab.unifi.base-url`: e.g. `https://192.168.1.1:8443`
- `homelab.unifi.username`, `homelab.unifi.password`
- `homelab.unifi.use-unifi-os`: `true` for UDM/UniFi OS; `false` for UniFi Network Application

If an agent URL or Unifi is missing or unreachable, the API falls back to mock data.

### Enabling Unifi (real devices on the dashboard)

1. **Do not put credentials in `application.yml`** – use `src/main/resources/application-local.yml` (add to `.gitignore` if you like).

2. **Create or edit `application-local.yml`** with:

   ```yaml
   homelab:
     unifi:
       enabled: true
       base-url: https://YOUR_CONTROLLER_IP:8443   # or https://YOUR_UDM_IP for UniFi OS (no port)
       username: your_admin_username
       password: your_admin_password
       use-unifi-os: false   # true if you have a UDM / UniFi OS device; false for standalone Network Application
   ```

3. **Base URL:**
   - **UniFi Network Application** (standalone controller): `https://192.168.x.x:8443`
   - **UniFi OS** (UDM, Dream Machine, etc.): `https://192.168.x.x` (port 443, often omit `:443`)

4. **Restart the backend** and open the Devices card – it should list clients from the default site. Self-signed controller certificates are accepted (for homelab use only).

## Build JAR (for deployment)

```bash
mvn clean package -DskipTests
java -jar target/home-lab-backend-0.1.0-SNAPSHOT.jar
```

### Speed test (real results)

The dashboard shows live speed test results when a Speedtest CLI is installed. The backend supports **both** Python speedtest-cli (`speedtest --json`) and Ookla CLI (`speedtest -f json`); it tries `--json` first, then `-f json`.

1. **Python CLI:** `pip install speedtest-cli`. Verify: `speedtest --json`.
2. **Ookla CLI (Ubuntu/Debian):** see https://www.speedtest.net/apps/cli. Verify: `speedtest -f json`.
(For Ookla install: packagecloud repo; verify with `speedtest -f json`.)

3. Result is **cached 10 minutes**; if no CLI works or the run fails, the card shows an error.

### Phase 3 – Govee (lights, plugs, appliances)

The dashboard can list Govee devices in two ways:

- **Cloud API:** needs an API key from the Govee Home app (Profile → Settings → Apply for API Key). Returns devices linked to that account.
- **LAN discovery:** no API key. Discovers devices on the **same network** as the backend (same subnet/VLAN; does not work across e.g. “secured” vs “main Wi‑Fi” zones). In the Govee Home app, open each device → **Settings** → turn **LAN** on. See [Govee WLAN guide](https://app-h5.govee.com/user-manual/wlan-guide).

**Config** (in `application-local.yml`, do not commit):
```yaml
homelab:
  govee:
    enabled: true
    api-key: ""                    # optional; omit or leave blank for LAN-only
    lan-discovery-enabled: true    # default; discover devices on LAN via UDP multicast
```
Restart the backend. The **Govee** card shows devices from cloud (if API key set) and/or LAN. Click the card for a table (name, model, type, IP for LAN, controllable). LAN discovery uses multicast `239.255.255.250:4001` and listens on UDP port 4002; ensure the backend host can send/receive on those.

## Next steps (you)

- Store secrets in env vars or `application-local.yml` (not committed).
