# GET /admin/indicators

- **UC / SPEC**: UC09 — [Consultar panel de administración](../../../features/009-consultar-panel-administracion/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Query

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `period` | enum `Period` | Sí | `WEEKLY` o `MONTHLY`. |

```http
GET /admin/indicators?period=WEEKLY
```

## Respuestas

### 200 OK

```json
{
  "period": "WEEKLY",
  "from": "2026-11-09T00:00:00-05:00",
  "to": "2026-11-16T00:00:00-05:00",
  "reservations": 128,
  "lateCancellations": 9,
  "noShows": 4,
  "suspendedUsers": 2
}
```

### 400 Bad Request

```json
{
  "code": "INVALID_PERIOD",
  "message": "El período debe ser WEEKLY o MONTHLY.",
  "details": {
    "field": "period"
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `INVALID_PERIOD` | 400 | `period` distinto de `WEEKLY` o `MONTHLY`. | OQ-08 |
| `VALIDATION_ERROR` | 400 | Falta `period`. | — |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- Los períodos son fijos semanal y mensual; el frontend permite elegir cuál ver (OQ-08).
- El período actual es `[from, to)` en America/Bogota. `reservations` cuenta creaciones registradas en el período; `lateCancellations` y `noShows` cuentan transiciones registradas en el período; `suspendedUsers` cuenta usuarios distintos suspendidos durante cualquier parte del período, según auditoría de suspensión y su fecha de fin.
- El solapamiento usa intervalos semiabiertos: inicio de suspensión anterior a `to` y fin posterior a `from`. Se cuentan usuarios distintos a partir del historial `monthly_suspension`; no es posible reconstruir automáticamente las fechas de fin históricas no conservadas.
- **Desviación pendiente:** el conteo implementado consulta `monthly_suspension` y no consulta los registros de auditoría `USER_SUSPENDED` exigidos para esta métrica.
- `lateCancellations` cuenta las reservas con cancelación auditada en el período cuyo estado es `CANCELLED_LATE`.
- El período mensual es el mes calendario en curso, desde el día 1 a las 00:00 en America/Bogota.
- El período semanal comienza el lunes a las 00:00 en America/Bogota (RNF-07).
- `lateCancellations` y `noShows` siguen las definiciones del contexto (RN-22, RN-37).
