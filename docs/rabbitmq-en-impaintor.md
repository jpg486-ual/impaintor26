# RabbitMQ en Impaintor

Este documento describe cómo se integra RabbitMQ en el flujo realtime de Impaintor.

## Resumen rápido

- El cliente (navegador) se conecta por **WebSocket/SockJS** a `GET /api/ws`.
- El backend Spring actúa como **STOMP broker relay**.
- RabbitMQ recibe/publica los frames STOMP en `:61613`.
- El estado de juego se sigue calculando en backend; RabbitMQ solo transporta mensajes realtime.

## Arquitectura de comunicación

```text
Browser (STOMP over WS)
        |
        v
Frontend Nginx (/api/ws -> backend)
        |
        v
Spring Boot WebSocket endpoint (/ws)
        |
        v
StompBrokerRelayMessageHandler
        |
        v
RabbitMQ STOMP plugin (:61613)
```

> Ver "Conectado (WebSocket)" en la UI es normal: ese texto representa el tramo navegador→backend. RabbitMQ está en el tramo backend→broker.

## Configuración actual

### Backend (`backend/src/main/resources/application.properties`)

Propiedades relevantes:

- `app.ws.broker.relay-enabled` (`false` por defecto)
- `app.ws.broker.relay-host`
- `app.ws.broker.relay-port` (61613)
- `app.ws.broker.client-login` / `client-passcode`
- `app.ws.broker.system-login` / `system-passcode`
- `app.ws.broker.virtual-host`

Cuando `relay-enabled=true`, `WebSocketConfig` usa `enableStompBrokerRelay("/topic")`.
Cuando `relay-enabled=false`, usa el `simpleBroker` embebido.

### Docker Compose

`docker-compose.yml` levanta:

- `rabbitmq:4-management`
- plugin STOMP habilitado mediante `rabbitmq/enabled_plugins`
- backend con `APP_WS_BROKER_RELAY_ENABLED=true`

Puertos:

- `61613`: STOMP (relay)
- `15672`: panel de management
- `5672`: AMQP

## Convención de destinos STOMP

Para compatibilidad con RabbitMQ STOMP topics en este proyecto, los destinos de jugador se publican/suscriben con formato:

`/topic/room.<ROOM_CODE>.player.<PLAYER_ID>`

Ejemplo:

`/topic/room.ABC123.player.42`

## Flujo realtime de estado

1. El cliente entra en sala y abre STOMP por `/api/ws`.
2. Se suscribe a su destino de jugador (`/topic/room.<code>.player.<id>`).
3. El backend, en `GameService.broadcastState`, construye estado personalizado por jugador.
4. El backend publica cada estado en su destino correspondiente.
5. El cliente actualiza estado local y countdown.
6. Si WS falla, frontend usa fallback de polling periódico (`GET /api/rooms/{code}/state`).

## Operación y verificación

### Levantar stack

```bash
docker compose up --build
```

### Comprobar servicios

```bash
docker compose ps
```

### Revisar logs

```bash
docker compose logs -f backend rabbitmq frontend
```

## Troubleshooting frecuente

### `Invalid destination` en RabbitMQ

Causa habitual: destino STOMP no compatible (por ejemplo con `/` en segmentos de topic).  
En Impaintor debe usarse `room.<code>.player.<id>` bajo `/topic`.

### 502 en frontend al crear sala

Normalmente significa que backend no está arriba o está reiniciando.  
Revisar `docker compose ps` y logs del backend.

### Reconexiones WS pero juego funcional

Puede haber reconexión de transporte mientras el polling mantiene estado consistente.  
Revisar errores STOMP y destinos; si hay `Invalid destination`, corregir naming del topic.
