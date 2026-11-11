# GET /admin/occupancy

- **UC / SPEC**: UC09 — [Consultar panel de administración](../../../features/009-consultar-panel-administracion/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Query

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `date` | `YYYY-MM-DD` | Sí | Fecha a consultar. |
| `discipline` | enum `Discipline` | No | Filtra por disciplina. |
| `spaceId` | UUID | No | Filtra por espacio. |

```http
GET /admin/occupancy?date=2026-11-11&discipline=VOLLEYBALL
```

## Respuestas

### 200 OK

```json
{
  "date": "2026-11-11",
  "spaces": [
    {
      "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "spaceName": "Cancha de voleibol 1",
      "slots": [
        {
          "startAt": "2026-11-11T14:00:00-05:00",
          "endAt": "2026-11-11T15:00:00-05:00",
          "status": "OCCUPIED",
          "reservationId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"
        },
        {
          "startAt": "2026-11-11T15:00:00-05:00",
          "endAt": "2026-11-11T16:00:00-05:00",
          "status": "BLOCKED",
          "reservationId": null
        }
      ]
    }
  ]
}
```

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La fecha es obligatoria.",
  "details": {
    "fields": { "date": "valor inválido" }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Falta `date` o un filtro tiene formato inválido. | — |
| `INVALID_DATE` | 400 | `date` no tiene formato válido. | — |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- Es una consulta de solo lectura (RF-14).
- `status` usa los mismos valores que el calendario: `FREE`, `OCCUPIED`, `BLOCKED`.
- Un espacio compartido entre disciplinas aparece ocupado para cualquiera de ellas (RN-07).
