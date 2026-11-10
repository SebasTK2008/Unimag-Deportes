# GET /quota/me

- **UC / SPEC**: UC08 — [Consultar cuota semanal](../../../features/008-consultar-cuota-semanal/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

Sin parámetros.

## Respuestas

### 200 OK

```json
{
  "weekStart": "2026-11-09T00:00:00-05:00",
  "weekEnd": "2026-11-16T00:00:00-05:00",
  "limitHours": 4,
  "usedHours": 3,
  "availableHours": 1,
  "resetAt": "2026-11-16T00:00:00-05:00"
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |
| `PROFILE_INCOMPLETE` | 403 | Falta el nombre del primer ingreso. | RN-59 |

## Notas

- La cuota se calcula sobre las reservas que cuentan en la semana, no se almacena como saldo (RN-16, RN-17).
- Cuentan: confirmada, en uso, completada, cancelada tardía e inasistencia. No cuentan: cancelada a tiempo y cancelada por el administrador (sección 5.4 del contexto).
- La semana comienza el lunes a las 00:00 en America/Bogota (RNF-07).
