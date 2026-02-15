# Backend (Spring Boot)

REST API for the home monitoring dashboard. Serves mock data for Phase 1; later aggregates Unifi, Python agents, speed tests, and Govee.

## Run locally

**Requirements:** Java 17+, Maven 3.6+

```bash
cd backend
mvn spring-boot:run
```

If you have the Maven wrapper: `./mvnw spring-boot:run`.  
To generate the wrapper (one-time): `mvn -N wrapper:wrapper`.

- **API:** http://localhost:8080/api/server-stats, /api/desktop-stats, /api/devices, /api/speed-test  
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

## Next steps (you)

- Add Govee endpoints and scheduled speed test (Phase 3).
- Store secrets in env vars or `application-local.yml` (not committed).
