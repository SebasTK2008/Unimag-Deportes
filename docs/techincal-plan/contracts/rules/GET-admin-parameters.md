# GET /admin/parameters

- **UC / SPEC**: UC06 — [Reglas de uso](../../../features/006-reglas-de-uso/spec.md)
- **Rol**: Administrador
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
    "minAdvanceMinutes": 0,
    "bookingToleranceMinutes": 10
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
    "closesAtReservationEnd": true
  },
  "reminder": {
    "hoursBefore": 2
  },
  "sanctions": {
    "noShowsForSuspension": 3,
    "window": "CALENDAR_MONTH",
    "suspensionDays": 7
  },
  "emailDomain": "@unimagdalena.edu.co"
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- Muestra también valores fijos y de despliegue; solo `quota.weeklyHours` y `sanctions.noShowsForSuspension` se editan mediante `PUT /admin/parameters`.
- Los parámetros de autenticación viven en `application.properties` y no se exponen ni se editan aquí (OQ-16).
