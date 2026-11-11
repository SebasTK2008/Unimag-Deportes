# GET /reservations/{id}/cancellation-preview

- **UC / SPEC**: UC04 — [Gestionar mis reservas](../../../features/004-gestionar-mis-reservas/spec.md)
- **Rol**: Usuario
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador de la reserva. |

```http
GET /reservations/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/cancellation-preview
```

## Respuestas

### 200 OK

Cancelación a tiempo:

```json
{
  "reservationId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "late": false,
  "hoursCounted": 0,
  "message": "La cancelación es a tiempo: el espacio queda libre y las horas se devuelven a tu cuota."
}
```

Cancelación tardía:

```json
{
  "reservationId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "late": true,
  "hoursCounted": 2,
  "message": "La cancelación es tardía: el espacio queda libre, pero las horas siguen contando como usadas."
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
| `RESERVATION_NOT_CANCELLABLE` | 409 | Ya tiene check-in o no se puede cancelar. | RN-24 |

## Notas

- La vista previa no cambia el estado de la reserva; solo informa el efecto sobre la cuota (RN-25).
- El umbral es configurable: por defecto, menos de 60 minutos es tardía (RN-21, RN-22).
- Las reservas canceladas conservan el registro de implementos y liberan su stock (OQ-19).
