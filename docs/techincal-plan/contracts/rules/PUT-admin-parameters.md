# PUT /admin/parameters

- **UC / SPEC**: UC06 — [Reglas de uso](../../../features/006-reglas-de-uso/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>` con reautenticación reciente (`reauthAt` de los últimos 10 minutos)

## Solicitud

### Cuerpo

Se envían solo los bloques o campos que se quieren modificar; el resto se conserva.

| Campo | Tipo | Descripción |
|---|---|---|
| `reservation.slotMinutes` | integer | Duración de la franja en minutos. |
| `reservation.maxSlotsPerReservation` | integer | Máximo de franjas consecutivas por reserva. |
| `reservation.maxAdvanceDays` | integer | Anticipación máxima en días. |
| `reservation.minAdvanceMinutes` | integer | Anticipación mínima en minutos. |
| `quota.weeklyHours` | integer | Cuota semanal por usuario. |
| `quota.weekStartsOn` | string | Día de inicio de la semana (`MONDAY`). |
| `cancellation.lateThresholdMinutes` | integer | Umbral de cancelación tardía. |
| `checkIn.opensMinutesBefore` | integer | Minutos antes del inicio en que abre el check-in. |
| `checkIn.closesAtToleranceEnd` | boolean | Si la ventana cierra al terminar la tolerancia. |
| `checkIn.toleranceBookingMinutes` | integer | Plazo de check-in para reservas hechas en tolerancia. |
| `reminder.hoursBefore` | integer | Horas antes del inicio del recordatorio. |
| `sanctions.noShowsForSuspension` | integer | Inasistencias que provocan suspensión. |
| `sanctions.windowDays` | integer | Ventana rodante de conteo. |
| `sanctions.suspensionDays` | integer | Duración de la suspensión. |
| `emailDomain` | string | Dominio institucional permitido. |

```json
{
  "quota": {
    "weeklyHours": 5
  },
  "cancellation": {
    "lateThresholdMinutes": 45
  }
}
```

## Respuestas

### 200 OK

Devuelve el objeto completo actualizado (misma forma que `GET /admin/parameters`).

```json
{
  "reservation": {
    "slotMinutes": 60,
    "maxSlotsPerReservation": 2,
    "maxAdvanceDays": 7,
    "minAdvanceMinutes": 0
  },
  "quota": {
    "weeklyHours": 5,
    "weekStartsOn": "MONDAY",
    "timeZone": "America/Bogota"
  },
  "cancellation": {
    "lateThresholdMinutes": 45
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

### 403 Forbidden

```json
{
  "code": "REAUTH_REQUIRED",
  "message": "Debes reautenticarte para cambiar las reglas.",
  "details": {}
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Valor fuera de rango o tipo inválido. | — |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `REAUTH_REQUIRED` | 403 | No hay reautenticación de los últimos 10 minutos. | D-21 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- Los cambios rigen solo para operaciones nuevas; no modifican reservas existentes (OQ-05).
- Los parámetros de autenticación no forman parte de este contrato (OQ-16).
- Si el cliente recibe `REAUTH_REQUIRED`, debe pedir un código con `POST /auth/reauth/code` y validarlo con `POST /auth/reauth/verify`.
