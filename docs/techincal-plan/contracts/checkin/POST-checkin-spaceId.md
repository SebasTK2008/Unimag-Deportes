# POST /checkin/{spaceId}

- **UC / SPEC**: UC05 — [Confirmar asistencia (QR)](../../../features/005-confirmar-asistencia-qr/spec.md)
- **Rol**: Usuario, Administrador (solo sus propias reservas)
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `spaceId` | UUID | Espacio cuyo QR se escaneó. |

Sin cuerpo. La identidad sale del token, nunca del QR (RN-32).

```http
POST /checkin/3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d
```

## Respuestas

### 200 OK

```json
{
  "reservationId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "status": "IN_USE",
  "checkedInAt": "2026-11-11T13:58:00-05:00"
}
```

### 409 Conflict

```json
{
  "code": "CHECKIN_TOO_EARLY",
  "message": "El check-in aún no está disponible.",
  "details": {
    "opensAt": "2026-11-11T13:50:00-05:00"
  }
}
```

```json
{
  "code": "CHECKIN_WINDOW_CLOSED",
  "message": "La ventana de check-in ya cerró.",
  "details": {}
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | La reserva activa en ese espacio es de otro usuario. | RN-33 |
| `NO_ACTIVE_RESERVATION` | 404 | No hay reserva activa del usuario en ese espacio. | RN-33 |
| `CHECKIN_TOO_EARLY` | 409 | Aún no ha abierto la ventana configurada para la reserva. | RN-36 |
| `CHECKIN_WINDOW_CLOSED` | 409 | La ventana ya cerró. | RN-36 |
| `RESERVATION_CANCELLED` | 409 | La reserva fue cancelada. | RN-35 |
| `CHECKIN_SELECTION_REQUIRED` | 409 | Hay más de una reserva propia pendiente dentro de su ventana; se debe elegir por identificador. | RN-33 |

## Notas

- Endpoint conservado para compatibilidad. El flujo con selección usa [consulta de elegibles](GET-checkin-spaceId-reservations.md) y [confirmación por reserva](POST-checkin-spaceId-reservations-reservationId.md).
- La ventana abre según la configuración conservada por la reserva (10 minutos antes por defecto) y cierra una hora después del fin, inclusive (RN-30, RN-36).
- Si hay una única reserva propia `CONFIRMED` sin check-in dentro de su ventana, se confirma. Si hay varias, devuelve `CHECKIN_SELECTION_REQUIRED`, sin elegir ni modificar ninguna.
- Una reserva terminada puede registrar asistencia aunque otro usuario ocupe el espacio. Antes del fin, la ocupación por otro usuario sigue produciendo `FORBIDDEN`.
- Si no hay pendientes elegibles, conserva la búsqueda anterior como fallback: primero rechaza si hay otro usuario en curso; después selecciona la primera `CONFIRMED` o `IN_USE` con fin futuro, luego la primera con inicio no anterior a ahora y finalmente la última de la lista. Sin reservas devuelve `NO_ACTIVE_RESERVATION`. Las validaciones de estado y ventana determinan el resultado.
- El primer check-in desde el fin deja la reserva en `COMPLETED`; antes del fin queda `IN_USE`. Si la seleccionada ya está `IN_USE` o `COMPLETED` con asistencia, devuelve su fecha original y estado actual, sin otra auditoría. Para reintentos inequívocos se recomienda confirmar por identificador.
- Si el QR se abre sin sesión vigente o en otro navegador, el cliente lleva al login y vuelve a `/checkin/{spaceId}` (RN-34).
- El QR es estático y no identifica al usuario (RN-31, RN-32).
