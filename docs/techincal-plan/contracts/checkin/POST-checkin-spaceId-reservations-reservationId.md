# POST /checkin/{spaceId}/reservations/{reservationId}

- **UC / SPEC**: UC05 — [Confirmar asistencia](../../../features/005-confirmar-asistencia-qr/spec.md)
- **Rol**: Usuario, Administrador (solo reservas propias)
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

`spaceId`: UUID del espacio del QR o enlace. `reservationId`: UUID de la reserva elegida. Sin cuerpo.

## Respuestas

### 200 OK

Misma forma de respuesta que [`POST /checkin/{spaceId}`](POST-checkin-spaceId.md). Antes del fin, `status` es `IN_USE`; desde el fin es `COMPLETED`.

```json
{
  "reservationId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "status": "COMPLETED",
  "checkedInAt": "2026-11-11T16:30:00-05:00"
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `RESERVATION_NOT_FOUND` | 404 | No existe, es de otro usuario o no corresponde al espacio. | RN-33 |
| `RESERVATION_CANCELLED` | 409 | La reserva está cancelada. | RN-35 |
| `CHECKIN_TOO_EARLY` | 409 | Aún no abre; `details.opensAt` informa la apertura. | RN-36 |
| `CHECKIN_WINDOW_CLOSED` | 409 | Se superó el cierre o el estado no admite el primer registro. | RN-36 |
| `FORBIDDEN` | 403 | Otro usuario tiene una reserva en curso y la propia aún no terminó. | RN-36 |

También se aplican los errores comunes de autenticación y perfil.

## Notas

- Admite el primer check-in hasta `endAt + 60 minutos`, inclusive, con la hora del servidor después de adquirir el bloqueo transaccional compartido con el proceso de inasistencias. Pasado ese instante se rechaza, aunque el job todavía no haya ejecutado.
- Una reserva terminada puede confirmar asistencia aunque otro usuario ya ocupe el espacio. No amplía el horario reservado.
- Guarda `checkedInAt` con precisión de microsegundos y una única auditoría `CHECK_IN`. La validación temporal precede al truncado.
- Repetir sobre `IN_USE` o `COMPLETED` con asistencia devuelve la fecha original y el estado actual, incluso después del cierre, sin otra auditoría. Una reserva cancelada o declarada `NO_SHOW` no se reactiva.
