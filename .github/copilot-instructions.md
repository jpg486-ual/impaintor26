# Copilot Instructions for `impaintor26`

## Build, test, and lint commands

Use repository root unless noted.

| Area | Command | Notes |
| --- | --- | --- |
| Full stack (Docker) | `docker compose up --build` | Starts PostgreSQL, Spring Boot backend, and Angular+Nginx frontend. |
| Backend tests (all) | `cd backend && ./mvnw test` | Runs JUnit suite (includes SpringBoot tests and service/unit tests). |
| Backend test (single class) | `cd backend && ./mvnw -Dtest=GameServiceTurnModeTests test` | Use for focused backend iteration. |
| Backend test (single method) | `cd backend && ./mvnw -Dtest=GameServiceTurnModeTests#turnMode_allowsVotingDuringDrawing test` | Fastest loop for one scenario. |
| Backend package | `cd backend && ./mvnw -DskipTests package` | Produces runnable JAR in `backend/target/`. |
| Backend run (local) | `cd backend && ./mvnw spring-boot:run` | Uses `application.properties` and env overrides. |
| Frontend dev server | `cd frontend && npm start` | Angular dev server. |
| Frontend build | `cd frontend && npm run build` | Production build to `frontend/dist/first-app/`. |
| Frontend watch build | `cd frontend && npm run watch` | Development build in watch mode. |

There are currently no dedicated lint scripts configured in `frontend/package.json` or Maven lint plugins configured in `backend/pom.xml`.

## High-level architecture

- The app is a room-based multiplayer game with hidden-role logic. Frontend is Angular (standalone components + signals), backend is Spring Boot (`/api` context path), persistence is PostgreSQL (H2 in tests), and realtime state is pushed through STOMP endpoints (`/ws`).
- Game domain is in `backend/src/main/java/com/example/demo/game/`:
  - `GameService` owns game rules and phase transitions.
  - `GameController` exposes REST endpoints under `/rooms`.
  - JPA entities (`GameRoom`, `GamePlayer`, `DrawingStroke`, `GameVote`) persist room/player/stroke/vote state.
  - `GameScheduler` advances expired phases every 2s and cleans old rooms every 30s.
- State delivery is **personalized per player**:
  - Backend builds per-player `GameStateResponse` (e.g., impostor does not receive `yourWord`).
  - Backend publishes to `/topic/room/{roomCode}/player/{playerId}`.
  - Frontend subscribes per player and keeps polling fallback every 3s.
- Messaging backend can run in two modes:
  - Local simple broker (`app.ws.broker.relay-enabled=false`).
  - STOMP broker relay to RabbitMQ (`app.ws.broker.relay-enabled=true`, used in docker-compose).
- Timers are backend-authoritative:
  - Backend sets absolute `phaseEndsAt`.
  - Frontend computes countdown locally from `phaseEndsAt` and resyncs from REST/WS.
- Startup data safety:
  - `GameDataIntegrityGuard` runs at app ready, attempts known schema drift repairs, checks integrity, and resets game tables if corruption/inconsistency is detected.

## Structural direction in progress

- `TURN_BASED` UX now uses a **single large canvas** for the active drawer while other players observe in real time.
- Docker local stack enables RabbitMQ STOMP relay for inter-session messaging.
- Keep domain/game rules in `GameService`; avoid coupling game logic to transport or broker-specific concerns.
- Preserve per-player visibility semantics (impostor/word masking) regardless of transport changes.

## Key conventions in this codebase

- **Room code and identity normalization**
  - Room codes are treated uppercase in service lookups (`roomCode.toUpperCase(Locale.ROOT)`).
  - Player names are trimmed/validated and must be unique per room (case-insensitive).
- **Host authorization convention**
  - Host checks are name-based (`player.name == room.hostName`), not a stored host player ID.
- **Intentional idempotency in critical actions**
  - `startGame` returns without error if already started.
  - Duplicate votes are ignored.
  - `skipVoting` in invalid phase returns without error.
- **Turn-based mode semantics (important)**
  - Frontend `TURN_BASED` flow renders one shared turn canvas (not a 6-canvas grid).
  - `TURN_BASED` allows voting during `DRAWING` (not only `VOTING`).
  - Turn rotation is deterministic per room+round (`buildTurnOrder` with seeded shuffle).
  - Each new turn clears round strokes (`deleteByRoomCodeAndRoundNumber`) so active drawer starts with a clean board.
  - Host can force round resolution via `POST /rooms/{roomCode}/votes/skip`.
- **Stroke payload convention**
  - Frontend sends normalized points `[0..1000]`; backend stores as compact `x:y;x:y` text and decodes on response.
- **Room lifecycle and cleanup**
  - Finished rooms are short-lived (`ROOM_TTL_AFTER_FINISH_SECONDS = 60`).
  - Turn-based rooms also have a hard TTL from creation (`ROOM_TTL_SECONDS = 3600`) to avoid indefinite lingering sessions.
- **Error handling and debug mode**
  - API errors use `ApiExceptionHandler` response shape.
  - `APP_DEBUG_ERRORS=true` adds exception type/message in `details` for frontend toast display.
- **TDD and project-state discipline (required for new changes)**
  - Follow TDD: write/adjust failing tests first, implement, then refactor safely.
  - Re-validate project state continuously during implementation (tests/build for touched areas).
  - Document relevant architectural/behavior changes in repo docs when they affect game flow, transport, or operational setup.
