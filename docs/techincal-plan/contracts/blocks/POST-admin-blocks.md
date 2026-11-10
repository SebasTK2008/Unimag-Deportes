# POST /admin/blocks

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>` con reautenticación reciente (`reauthAt` de los últimos 10 minutos)

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `spaceId` | UUID | Sí | Espacio a bloquear. |
| `type` | enum `BlockType` | Sí | `CLASS`, `EVENT` o `MAINTENANCE`. |
| `reason` | string | Sí | Motivo visible en el calendario. |
| `startAt` | instant | Sí | Inicio del bloqueo. |
| `endAt` | instant | Sí | Fin del bloqueo. |
| `recurrence` | object | No | Solo para bloqueos recurrentes: `daysOfWeek` (array de `MONDAY`…`SUNDAY`) y `until` (`YYYY-MM-DD`). Si se omite, el bloqueo es puntual. |

```json
{
  "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "type": "CLASS",
  "reason": "Clase de voleibol",
  "startAt": "2026-11-11T08:00:00-05:00",
  "endAt": "2026-11-11T12:00:00-05:00",
  "recurrence": {
    "daysOfWeek": ["MONDAY", "WEDNESDAY"],
    "until": "2026-12-15"
  }
}
```

## Respuestas

### 201 Created

```json
{
  "recurrenceGroup": "2f4a6b8c-1d3e-4f5a-9b7c-8d6e4f2a1b3c",
  "createdBlocks": 20,
  "cancelledReservations": 3
}
```

- Si el bloqueo es puntual, `recurrenceGroup` es `null` y `createdBlocks` es `1`.
- `cancelledReservations` indica cuántas reservas se cancelaron automáticamente (RN-43).

### 403 Forbidden

```json
{
  "code": "REAUTH_REQUIRED",
  "message": "Debes reautenticarte para crear bloqueos institucionales.",
  "details": {}
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Fechas inválidas, `endAt` anterior a `startAt` o recurrencia mal formada. | RN-41 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `REAUTH_REQUIRED` | 403 | No hay reautenticación de los últimos 10 minutos. | D-21 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |
| `SPACE_NOT_FOUND` | 404 | El espacio no existe. | — |

## Notas

- Los bloqueos recurrentes se materializan en una fila por franja bloqueada y se agrupan por `recurrenceGroup` (OQ-06). No se editan por grupo en el prototipo: para cambiarlos se crea un bloqueo nuevo (OQ-06).
- Los bloqueos tienen prioridad sobre las reservas libres y cancelan las afectadas con estado `CANCELLED_BY_ADMIN`; esas horas no cuentan contra la cuota (RN-42, RN-43).
- Las franjas bloqueadas se muestran como no disponibles con el motivo (RN-44).
- Cada reserva cancelada genera un aviso por correo (RN-43, RN-47).
