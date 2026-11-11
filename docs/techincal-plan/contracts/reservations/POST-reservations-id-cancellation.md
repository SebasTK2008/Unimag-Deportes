# POST /reservations/{id}/cancellation

- **UC / SPEC**: UC04 — [Gestionar mis reservas](../../../features/004-gestionar-mis-reservas/spec.md)
- **Rol**: Usuario
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador de la reserva. |

Sin cuerpo.

```http
POST /reservations/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/cancellation
```

## Respuestas

### 200 OK

Cancelación a tiempo:

```json
{
  "id": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "status": "CANCELLED_ON_TIME",
  "cancelledAt": "2026-11-11T10:15:00-05:00",
  "hoursCounted": 0,
  "equipment": [
    {
      "equipmentId": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
      "name": "Balón de voleibol",
      "quantity": 1
    }
  ]
}
```

Cancelación tardía:

```json
{
  "id": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "status": "CANCELLED_LATE",
  "cancelledAt": "2026-11-11T13:30:00-05:00",
  "hoursCounted": 2,
  "equipment": [
    {
      "equipmentId": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
      "name": "Balón de voleibol",
      "quantity": 1
    }
  ]
}
```

### 409 Conflict

```json
{
  "code": "RESERVATION_NOT_CANCELLABLE",
  "message": "La reserva ya tiene check-in y no se puede cancelar.",
  "details": {}
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | La reserva no pertenece al usuario. | RN-03 |
| `RESERVATION_NOT_FOUND` | 404 | La reserva no existe. | — |
| `RESERVATION_NOT_CANCELLABLE` | 409 | Ya tiene check-in o ya está cancelada. | RN-24 |

## Notas

- El espacio queda libre en ambos casos; solo cambia si las horas cuentan contra la cuota (RN-21, RN-22).
- Cada cancelación genera una notificación por correo (RN-26).
- Los implementos quedan como historial y liberan su stock para otras reservas (RN-66, OQ-19).
