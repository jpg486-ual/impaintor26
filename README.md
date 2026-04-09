### Integrantes del equipo: 
* Juan Francisco Escobosa Brocal
* María López Espinosa
* Victor Vallejo Uroz
* Javier Saiz Gomez
* José Esteban Pérez González
* Abel Rodríguez Gutiérrez
* Teo Zaitegui Jódar 

## ImPaintor
Aplicación multijugador con interfaz gráfica basada en mecánicas de deducción y roles ocultos. El flujo central del juego desafía la capacidad de los usuarios para transmitir conceptos visuales sin revelar información clave al jugador que desconoce el contexto.

### ⚙️ Mecánica de Juego (Core Loop)
1.  **Asignación de Roles:** Al iniciar la ronda, el servidor asigna aleatoriamente el rol de "Impostor" a un único jugador.
2.  **Distribución de Datos:** El servidor selecciona una palabra (aleatoria o de un *pool* preaprobado) y la transmite a los clientes de todos los jugadores, aislando al Impostor (que no recibe la palabra).
3.  **Fase de Dibujo:** Los jugadores deben dibujar en el lienzo compartido pistas visuales relacionadas con el concepto (En caso de conocerlo).
4.  **Fase de Deducción:** A través de los trazos, los jugadores legítimos deben identificar quién está dibujando sin contexto (el Impostor), mientras que el Impostor debe intentar mimetizarse con el resto para no ser descubierto.

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

- En cada reinicio del backend se ejecuta una comprobación de integridad de datos de juego.
- Si se detectan inconsistencias (por ejemplo, referencias huérfanas entre salas, jugadores, votos o trazos), se reinicializa todo el estado persistido del juego para arrancar en limpio.
- Si el reinicio detecta un desfase de esquema conocido (por ejemplo, columnas nuevas faltantes en `game_rooms`), intenta repararlo automáticamente y luego reinicializa los datos del juego.

### Timers y sincronización

- El backend marca el fin de fase con `phaseEndsAt` (timestamp absoluto).
- Cada cliente calcula su countdown local usando ese timestamp.
- El cliente refresca estado periódicamente para re-sincronizar cambios de fase.

### Debug de errores API

- Activar detalle de errores en backend:

```bash
APP_DEBUG_ERRORS=true docker compose up --build
```

- Con debug activo, la API incluye tipo/mensaje de excepción en `details`.
- El frontend muestra esos detalles en la notificación de error y la oculta automáticamente tras unos segundos.
