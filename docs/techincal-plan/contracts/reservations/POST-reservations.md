# POST /reservations

- **UC / SPEC**: UC03 — [Reservar espacio deportivo](../../../features/003-reservar-espacio-deportivo/spec.md); UC13 — [Solicitar implementos extras](../../../features/013-solicitar-implementos-extras/spec.md)
- **Rol**: Usuario
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `spaceId` | UUID | Sí | Espacio a reservar. |
| `startAt` | instant | Sí | Inicio de la primera franja, alineado a los bloques de 60 minutos. |
| `slots` | integer | Sí | Número de franjas consecutivas: `1` o `2`. |
| `equipment` | array | No | Implementos solicitados. Cada elemento: `equipmentId` (UUID) y `quantity` (entero > 0). |

```json
{
  "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "startAt": "2026-11-11T14:00:00-05:00",
  "slots": 2,
  "equipment": [
    {
      "equipmentId": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
      "quantity": 1
    }
  ]
}
```

## Respuestas

### 201 Created

```json
{
  "id": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "space": {
    "id": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "name": "Cancha de voleibol 1"
  },
  "startAt": "2026-11-11T14:00:00-05:00",
  "endAt": "2026-11-11T16:00:00-05:00",
  "status": "CONFIRMED",
  "createdAt": "2026-11-10T18:20:00-05:00",
  "checkedInAt": null,
  "equipment": [
    {
      "equipmentId": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
      "name": "Balón de voleibol",
      "quantity": 1
    }
  ]
}
```

La reserva se confirma al instante; el comprobante se envía por correo después del commit (RN-13, RN-45).

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La reserva debe tener una o dos franjas consecutivas.",
  "details": {
    "fields": {
      "slots": "debe ser 1 o 2"
    }
  }
}
```

### 403 Forbidden

```json
{
  "code": "SUSPENDED",
  "message": "Estás suspendido y no puedes crear reservas hasta el 2026-11-17.",
  "details": {
    "suspendedUntil": "2026-11-17T00:00:00-05:00"
  }
}
```

### 409 Conflict

```json
{
  "code": "SLOT_TAKEN",
  "message": "La franja ya no está disponible.",
  "details": {}
}
```

```json
{
  "code": "QUOTA_EXCEEDED",
  "message": "La reserva supera tu cuota semanal. Te queda 1 hora disponible.",
  "details": {
    "weeklyLimitHours": 4,
    "usedHours": 3,
    "requestedHours": 2,
    "availableHours": 1
  }
}
```

```json
{
  "code": "EQUIPMENT_UNAVAILABLE",
  "message": "No hay suficientes unidades de Balón de voleibol para esa franja.",
  "details": {
    "equipmentId": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
    "requested": 2,
    "available": 1
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Campos faltantes, `slots` fuera de 1–2 o `startAt` desalineado. | RN-15 |
| `INVALID_QUANTITY` | 400 | `quantity` menor o igual a cero. | RN-63 |
| `PROFILE_INCOMPLETE` | 403 | Falta el nombre del primer ingreso. | RN-59 |
| `SUSPENDED` | 403 | El usuario está suspendido. | RN-40 |
| `SPACE_NOT_FOUND` | 404 | El espacio no existe. | — |
| `EQUIPMENT_NOT_FOUND` | 404 | Alguno de los implementos no existe. | RN-62 |
| `SLOT_TAKEN` | 409 | La franja ya tiene una reserva activa. | RN-10 |
| `BLOCKED_SLOT` | 409 | La franja tiene un bloqueo institucional. | RN-42 |
| `SPACE_IN_MAINTENANCE` | 409 | El espacio está en mantenimiento. | RN-09 |
| `OUTSIDE_BOOKING_WINDOW` | 409 | Fuera de los 7 días o pasada la tolerancia. | RN-14, RN-27 |
| `OVERLAPPING_RESERVATION` | 409 | El usuario ya tiene una reserva solapada. | RN-11 |
| `QUOTA_EXCEEDED` | 409 | Supera la cuota semanal. | RN-18 |
| `EQUIPMENT_UNAVAILABLE` | 409 | Implemento inactivo o sin stock en la franja. | RN-62, RN-65 |

## Notas

- La verificación y el bloqueo de la franja ocurren en una sola transacción; si dos usuarios compiten, gana el primero (RN-10).
- La cuota se mide en horas reservadas y se calcula sobre la semana de la fecha de la franja (RN-16, RN-17).
- Los implementos son opcionales. Su disponibilidad es `stock` menos las cantidades de reservas activas que se solapan en el tiempo (RN-65).
- La identidad del usuario sale del token, nunca del cuerpo (RN-03).
