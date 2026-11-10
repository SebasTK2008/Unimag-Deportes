# GET /rules

- **UC / SPEC**: UC06 — [Reglas de uso](../../../features/006-reglas-de-uso/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

Sin parámetros.

## Respuestas

### 200 OK

```json
{
  "reservation": {
    "slotMinutes": 60,
    "maxSlotsPerReservation": 2,
    "maxAdvanceDays": 7,
    "minAdvanceMinutes": 0
  },
  "quota": {
    "weeklyHours": 4,
    "weekStartsOn": "MONDAY",
    "timeZone": "America/Bogota"
  },
  "cancellation": {
    "lateThresholdMinutes": 60
  },
  "checkIn": {
    "opensMinutesBefore": 10,
    "closesAtToleranceEnd": true,
    "toleranceBookingMinutes": 10
  },
  "reminder": {
    "hoursBefore": 2
  },
  "sanctions": {
    "noShowsForSuspension": 3,
    "windowDays": 30,
    "suspensionDays": 7
  },
  "emailDomain": "@unimagdalena.edu.co"
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |

## Notas

- Devuelve los parámetros **de negocio** vigentes; los de autenticación no se exponen aquí (OQ-16).
- Los valores provienen de `usage_parameters` y el Administrador puede cambiarlos con `PUT /admin/parameters` (RF-12).
- Las reglas de implementos (solicitud opcional y disponibilidad por stock) están en `POST /reservations`; esta consulta cubre cuota, tolerancia, cancelación, check-in y sanciones (RF-09).
