# PUT /admin/parameters

- **UC / SPEC**: UC06 — [Reglas de uso](../../../features/006-reglas-de-uso/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>` con reautenticación reciente (`reauthAt` de los últimos 10 minutos)

## Solicitud

### Cuerpo

Se envían solo los bloques o campos que se quieren modificar; el resto se conserva.

| Campo | Tipo | Descripción |
|---|---|---|
| `quota.weeklyHours` | integer | Cuota semanal por usuario. |
| `sanctions.noShowsForSuspension` | integer | Inasistencias que provocan suspensión. |

```json
{
  "quota": {
    "weeklyHours": 5
  },
  "sanctions": {
    "noShowsForSuspension": 3
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
