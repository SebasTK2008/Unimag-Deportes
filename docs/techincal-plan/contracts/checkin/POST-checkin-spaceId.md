# POST /checkin/{spaceId}

- **UC / SPEC**: UC05 — [Confirmar asistencia (QR)](../../../features/005-confirmar-asistencia-qr/spec.md)
- **Rol**: Usuario
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
  "message": "El check-in abre 10 minutos antes del inicio de la franja.",
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

## Notas

- La ventana abre según la configuración conservada por la reserva (10 minutos antes por defecto) y cierra al finalizarla, sin incluir ese instante (RN-30, RN-36).
- Se selecciona la próxima reserva aplicable del usuario para el espacio por hora de inicio; si hay una en curso, se considera antes que una futura.
- Si el QR se abre sin sesión vigente o en otro navegador, el cliente lleva al login y vuelve a `/checkin/{spaceId}` (RN-34).
- El QR es estático y no identifica al usuario (RN-31, RN-32).
