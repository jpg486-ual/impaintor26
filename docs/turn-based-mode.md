# Impaintor - Modo por turnos

## Resumen

El modo `TURN_BASED` permite que solo un jugador dibuje cada vez, con rotación de turno automática entre todos los jugadores de la sala y un canvas único principal.

## Reglas

- El host elige el modo al crear sala: `SIMULTANEOUS` o `TURN_BASED`.
- En `TURN_BASED`, cada turno individual usa `roundDurationSeconds` como duración máxima de dibujado para el jugador activo.
- En `TURN_BASED`, el frontend muestra un **canvas grande compartido** para toda la sala.
- Solo el jugador con `activeDrawerPlayerId` puede dibujar en ese instante.
- El resto de jugadores observa en tiempo real los trazos del jugador activo en ese mismo canvas.
- El orden de turnos se aleatoriza en cada ronda.
- En cada inicio de turno se limpian los trazos visibles del canvas para que el jugador activo dibuje desde cero.
- En `TURN_BASED`, la votación está disponible durante `DRAWING` (no espera a una fase separada de `VOTING`).
- Mientras no voten todos, los turnos de dibujo siguen rotando uno a uno de forma indefinida dentro de la ronda.
- El host puede forzar el cierre de la ronda en casos excepcionales con `POST /api/rooms/{roomCode}/votes/skip`.
- La ronda termina cuando:
  - votan todos los jugadores, o
  - el host omite la votación.
- Si la mayoría única acierta al impostor, jugadores normales +1 punto.
- Si fallan o hay empate, impostor +3 puntos.

## API y estado

`GET /api/rooms/{code}/state?playerId={id}` incluye:

- `gameMode`: `SIMULTANEOUS` | `TURN_BASED`
- `activeDrawerPlayerId`: jugador con turno activo (solo en modo por turnos durante dibujo)
- `phaseEndsAt`: temporizador del turno activo en `TURN_BASED`

`POST /api/rooms` acepta `gameMode` opcional:

```json
{
  "username": "Host",
  "roundDurationSeconds": 90,
  "votingDurationSeconds": 25,
  "maxRounds": 5,
  "themes": ["animales", "comida"],
  "gameMode": "TURN_BASED"
}
```

Si no se envía `gameMode`, se usa `SIMULTANEOUS` por defecto.

## Ranked público (TURN_BASED)

- Las partidas ranked públicas usan exclusivamente `TURN_BASED`.
- El matchmaking empareja por ELO y amplía la ventana de búsqueda en bloques de `±100` cada `10s`.
- Cuando se forma un grupo compatible, cada ticket pasa a `MATCH_PENDING_CONFIRMATION`.
- La confirmación del match se realiza con `POST /api/matchmaking/public/confirm` y ventana de `15s`.
- Si todos confirman dentro del tiempo, se crea la sala `PUBLIC_RANKED` y el ticket pasa a `MATCHED` con `matchedRoomCode`.
- Si vence la ventana, el intento pasa a estado terminal `EXPIRED`.

## Enfoque TDD aplicado

1. Se añadieron tests de servicio (`GameServiceTurnModeTests`) para definir:
  - voto disponible durante dibujo en `TURN_BASED`,
  - rotación continua de turnos sin transición obligatoria a `VOTING`,
  - omisión de ronda por host en casos excepcionales,
   - restricción de dibujo solo para turno activo,
  - rotación de turnos y limpieza de canvas al inicio de turno.
2. Se implementó la lógica backend para satisfacer esos tests.
3. Se actualizó frontend para exponer canvas único de turno activo, señalización de turno y control de omisión de votación para host.
