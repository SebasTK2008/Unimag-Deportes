# GET /checkin/{spaceId}/reservations

- **UC / SPEC**: UC05 — [Confirmar asistencia](../../../features/005-confirmar-asistencia-qr/spec.md)
- **Rol**: Usuario, Administrador (solo reservas propias)
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

`spaceId`: UUID del espacio identificado por el QR o enlace. Sin cuerpo.

## Respuestas

### 200 OK

Lista ordenada por inicio. Solo incluye reservas del usuario autenticado para ese espacio, `CONFIRMED`, sin check-in y dentro de la ventana temporal. No reserva ni confirma asistencia.

```json
[
  {
    "reservationId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "startAt": "2026-11-11T14:00:00-05:00",
    "endAt": "2026-11-11T16:00:00-05:00",
    "opensAt": "2026-11-11T13:50:00-05:00",
    "closesAt": "2026-11-11T17:00:00-05:00"
  }
]
```

Devuelve `[]` si no hay reservas elegibles, incluso si no existe el espacio. No revela reservas de otros usuarios.

## Errores del dominio

Se aplican los errores comunes de autenticación y perfil del [contrato general](../README.md).

## Notas

- Ambos extremos de la ventana son inclusivos; la hora es la del servidor. La apertura usa la configuración conservada en la reserva; el cierre es una hora después de su fin.
- Se listan las reservas temporalmente elegibles; una reserva anticipada puede todavía recibir `FORBIDDEN` al confirmar si otro usuario ocupa la franja actual. La confirmación tardía no tiene ese impedimento.
- El cliente confirma cada elección con [`POST /checkin/{spaceId}/reservations/{reservationId}`](POST-checkin-spaceId-reservations-reservationId.md). La confirmación revalida todas las condiciones; la lista puede quedar desactualizada.
- Dos reservas propias consecutivas de una hora pueden aparecer simultáneamente. Si son de usuarios distintos, cada usuario solo ve la suya.
