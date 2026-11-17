# GET /spaces/{id}/availability

- **UC / SPEC**: UC02 — [Consultar disponibilidad](../../../features/002-consultar-disponibilidad/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador del espacio. |

### Query

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `date` | `YYYY-MM-DD` | Sí | Una fecha desde hoy hasta hoy + 6 días inclusive (7 fechas contando hoy), según America/Bogota. |
| `discipline` | enum `Discipline` | No | Disciplina con la que se consulta el espacio compartido. |

```http
GET /spaces/3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/availability?date=2026-11-11&discipline=VOLLEYBALL
```

## Respuestas

### 200 OK

```json
{
  "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "date": "2026-11-11",
  "discipline": "VOLLEYBALL",
  "slots": [
    {
      "startAt": "2026-11-11T14:00:00-05:00",
      "endAt": "2026-11-11T15:00:00-05:00",
      "status": "FREE",
      "bookable": true,
      "reason": null
    },
    {
      "startAt": "2026-11-11T15:00:00-05:00",
      "endAt": "2026-11-11T16:00:00-05:00",
      "status": "BLOCKED",
      "bookable": false,
      "reason": "Clase de voleibol"
    }
  ]
}
```

- `status`: `FREE`, `OCCUPIED` o `BLOCKED`.
- `bookable` indica si la franja se puede reservar en el momento de la consulta; una franja `FREE` con tolerancia vencida tiene `bookable: false`.
- `reason` solo tiene valor cuando la franja está bloqueada (RN-44).

### 400 Bad Request

Ejemplo si hoy es `2026-11-11` en America/Bogota:

```json
{
  "code": "OUTSIDE_CALENDAR_HORIZON",
  "message": "El calendario muestra 7 días contando hoy, hasta hoy + 6 días inclusive.",
  "details": {
    "maxDate": "2026-11-17"
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `INVALID_DATE` | 400 | `date` no tiene formato válido. | — |
| `OUTSIDE_CALENDAR_HORIZON` | 400 | La fecha es anterior a hoy o posterior a hoy + 6 días en America/Bogota. | OQ-09 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `SPACE_NOT_FOUND` | 404 | El espacio no existe. | — |

## Notas

- Las franjas son de 60 minutos dentro del horario de operación (RN-08).
- Una franja libre que ya comenzó sigue disponible hasta el fin de la tolerancia (RN-27, RN-28).
- El espacio en mantenimiento no se muestra como reservable (RN-09).
- Se mantiene una fecha por petición; la respuesta no agrupa los siete días.
