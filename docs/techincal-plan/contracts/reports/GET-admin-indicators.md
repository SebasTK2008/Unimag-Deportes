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
- El período semanal comienza el lunes a las 00:00 en America/Bogota (RNF-07).
- `lateCancellations` y `noShows` siguen las definiciones del contexto (RN-22, RN-37).
