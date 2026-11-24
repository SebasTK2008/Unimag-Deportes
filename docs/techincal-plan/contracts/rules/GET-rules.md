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
    "maxAdvanceDays": 6,
    "minAdvanceMinutes": 0,
    "bookingToleranceMinutes": 10
  },
  "quota": {
    "weeklyHours": 4,
    "dailyHours": 2,
    "weekStartsOn": "MONDAY",
    "timeZone": "America/Bogota"
  },
  "cancellation": {
    "lateThresholdMinutes": 60
  },
  "checkIn": {
    "opensMinutesBefore": 10,
    "closesAtReservationEnd": false,
    "closesMinutesAfterEnd": 60
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
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |

## Notas

- Devuelve los parámetros **de negocio** vigentes; los de autenticación no se exponen aquí (OQ-16).
- `quota.dailyHours` es fijo: 2 horas por fecha en America/Bogota entre todos los espacios, con los mismos estados de consumo de la cuota semanal. `checkIn.closesMinutesAfterEnd` es fijo y el cierre es inclusivo.
- Cuota y umbral se leen de la versión señalada por `usage_parameters_head`. El historial se conserva en base de datos y no cambia la forma de esta respuesta.
- Solo cuota y umbral de inasistencias provienen de `usage_parameters` y se editan con `PUT /admin/parameters`; los demás son fijos o de despliegue (RF-12).
- Las reglas de implementos (solicitud opcional y disponibilidad por stock) están en `POST /reservations`; esta consulta cubre cuota, tolerancia, cancelación, check-in y sanciones (RF-09).
- `maxAdvanceDays` es el desplazamiento máximo de fecha desde hoy: `6` permite 7 fechas calendario, hoy…hoy + 6 inclusive en America/Bogota, hasta finalizar la última fecha y sujeto al horario del espacio. Es fijo, no editable.
