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

## Build JAR (for deployment)

```bash
mvn clean package -DskipTests
java -jar target/home-lab-backend-0.1.0-SNAPSHOT.jar
```

## Next steps (you)

- Add Govee endpoints and scheduled speed test (Phase 3).
- Store secrets in env vars or `application-local.yml` (not committed).
