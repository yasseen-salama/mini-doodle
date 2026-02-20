# Mini Doodle

A meeting scheduling API. Users create time slots, share their availability, and book slots as meetings with other participants.

---

## Running it

**Requirements:** Docker and Docker Compose. 
```bash
git clone 
cd mini-doodle
docker-compose up --build
```

## What This Is
- User registration with one calendar per user
- Time slot management (`FREE` / `BUSY`)
- Meeting scheduling on top of slots
- Availability lookup for any user


Stop:
```bash
docker compose down
```

## URLs
- API base: `http://localhost:8080`
- Swagger UI (interactive docs): `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Prometheus UI: `http://localhost:9090`
- Grafana: `http://localhost:3000` (`admin` / `admin`)

## API Endpoints
Public:
- `POST /api/users/register`
- `GET /actuator/health`
- `GET /actuator/prometheus`

Authenticated (HTTP Basic):
- `POST /api/slots`
- `GET /api/slots?from=&to=&page=&size=`
- `GET /api/slots/{id}`
- `PATCH /api/slots/{id}`
- `DELETE /api/slots/{id}`
- `POST /api/meetings`
- `GET /api/meetings?from=&to=&page=&size=`
- `GET /api/meetings/{id}`
- `PATCH /api/meetings/{id}`
- `DELETE /api/meetings/{id}`
- `GET /api/availability?userId=&from=&to=`

## Notes
- Liquibase runs automatically at app startup.
- Dev seed user runs only with Liquibase context `dev` (set in docker-compose).

## Run Tests
```bash
./gradlew test
```
