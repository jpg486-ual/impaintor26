# Impaintor - Modo por turnos

## Resumen

El modo `TURN_BASED` permite que solo un jugador dibuje cada vez, con rotación de turno automática entre todos los jugadores de la sala.

## Reglas

- El host elige el modo al crear sala: `SIMULTANEOUS` o `TURN_BASED`.
- En `TURN_BASED`, cada turno individual usa `roundDurationSeconds` como duración máxima para el jugador activo.
- Solo el jugador con `activeDrawerPlayerId` puede dibujar en ese instante.
- En cada cambio de turno se limpian los trazos visibles del canvas para que el siguiente jugador dibuje desde cero.
- Cualquier jugador puede votar desde el menú lateral durante la fase de dibujo.
- La ronda termina cuando:
  - votan todos los jugadores, o
  - todos los jugadores completan su turno de dibujo.
- Si la mayoría única acierta al impostor, jugadores normales +1 punto.
- Si fallan o hay empate, impostor +3 puntos.

## API y estado

`GET /api/rooms/{code}/state?playerId={id}` incluye:

- `gameMode`: `SIMULTANEOUS` | `TURN_BASED`
- `activeDrawerPlayerId`: jugador con turno activo (solo en modo por turnos durante dibujo)

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

## Enfoque TDD aplicado

1. Se añadieron tests de servicio (`GameServiceTurnModeTests`) para definir:
   - votación durante dibujo en `TURN_BASED`,
   - restricción de dibujo solo para turno activo,
   - rechazo de voto en dibujo para `SIMULTANEOUS`.
2. Se implementó la lógica backend para satisfacer esos tests.
3. Se actualizó frontend para exponer el modo y la votación lateral.
