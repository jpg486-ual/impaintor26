## Impaintor

Juego multijugador tipo "impostor" para hasta 6 jugadores, con dibujo en tiempo real por rondas.

### Stack

- Frontend: Angular servido por Nginx (`http://localhost:4200`)
- Backend: Spring Boot API REST bajo `/api` (`http://localhost:8080/api`)
- Base de datos: PostgreSQL 16 (`localhost:5432`)

### Flujo del juego

- Un jugador crea sala y configura duración de dibujo, duración de votación, rondas y temas.
- Los jugadores se unen con nombre único por sala.
- En cada ronda se asigna 1 impostor (sin palabra) y al resto una palabra aleatoria según temas.
- Todos dibujan simultáneamente en su espacio dentro de un tablero de 6 canvases.
- Al terminar el tiempo, todos votan quién es el impostor.
- Si aciertan mayoría única, jugadores normales ganan 1 punto.
- Si fallan o hay empate, gana el impostor con 3 puntos.
- Al finalizar la partida, la sala queda efímera y se elimina automáticamente junto con sus datos.

### API principal

- `POST /api/rooms` crear sala
- `POST /api/rooms/{roomCode}/join` unirse
- `POST /api/rooms/{roomCode}/start` iniciar partida
- `GET /api/rooms/{roomCode}/state?playerId={id}` estado de partida
- `POST /api/rooms/{roomCode}/strokes` enviar trazo
- `POST /api/rooms/{roomCode}/votes` votar

### Arranque local con Docker

```bash
docker compose up --build
```
