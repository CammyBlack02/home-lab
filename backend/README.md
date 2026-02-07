# Backend (Spring Boot)

REST API for the home monitoring dashboard. Aggregates data from Unifi, Python agents (server + desktop), speed tests, and Govee; serves the frontend.

**Next steps (Phase 1):**

- Initialise Spring Boot project (e.g. [start.spring.io](https://start.spring.io): Web, Java 17+).
- Add endpoints with mock data: `GET /api/server-stats`, `GET /api/devices`, `GET /api/speed-test`.
- Serve static frontend from `src/main/resources/static` or similar.

Run from this directory once the project is generated: `./mvnw spring-boot:run` or `./gradlew bootRun`.
