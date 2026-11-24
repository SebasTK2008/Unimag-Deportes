# GET /spaces/{id}/availability/stream

- **UC / SPEC**: UC02 — [Consultar disponibilidad](../../../features/002-consultar-disponibilidad/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`
- **Transporte**: Server-Sent Events (SSE)

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador del espacio. |

### Headers

```http
Accept: text/event-stream
Authorization: Bearer <token>
```

### Cliente

El cliente debe abrir el flujo con `fetch` en modo *streaming* (o una librería SSE que acepte encabezados). `EventSource` nativo no permite enviar `Authorization`, y el token **nunca** debe ir en la URL (§5.3 del plan).

## Respuestas

### 200 OK

```http
Content-Type: text/event-stream
Cache-Control: no-cache
```

El servidor emite un evento cada vez que cambia la disponibilidad del espacio (reserva, cancelación, bloqueo o cambio de estado):

```text
event: availability-changed
data: {"spaceId":"3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","changedAt":"2026-11-11T09:15:00-05:00"}

```

El cliente, al recibir el evento, vuelve a pedir `GET /spaces/{id}/availability?date=...`.

### 401 Unauthorized

```json
{
  "code": "TOKEN_EXPIRED",
  "message": "La sesión venció. Inicia sesión de nuevo.",
  "details": {}
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció al abrir el flujo. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |
| `SPACE_NOT_FOUND` | 404 | El espacio no existe. | — |

## Notas

- Los eventos no envían el calendario completo, solo la notificación; el cliente recarga el calendario por REST.
- La conexión se mantiene abierta; el cliente la cierra al cambiar de pantalla.
