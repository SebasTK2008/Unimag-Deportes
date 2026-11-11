# GET /equipment/availability

- **UC / SPEC**: UC13 — [Solicitar implementos extras](../../../features/013-solicitar-implementos-extras/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Query

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `startAt` | instant | Sí | Inicio de la ventana a consultar (puede ser una franja o un rango). |
| `endAt` | instant | Sí | Fin de la ventana a consultar. |
| `discipline` | enum `Discipline` | No | Filtra por disciplina sugerida. |

```http
GET /equipment/availability?startAt=2026-11-11T14:00:00-05:00&endAt=2026-11-11T16:00:00-05:00
```

## Respuestas

### 200 OK

```json
{
  "startAt": "2026-11-11T14:00:00-05:00",
  "endAt": "2026-11-11T16:00:00-05:00",
  "items": [
    {
      "equipmentId": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
      "name": "Balón de voleibol",
      "stock": 10,
      "committed": 3,
      "available": 7
    }
  ]
}
```

- `committed`: máximo de unidades comprometidas simultáneamente por reservas activas durante la ventana, no la suma de reservas consecutivas.
- `available`: `stock - committed`; es el máximo que se puede pedir para esa ventana.

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "El fin de la ventana debe ser posterior al inicio.",
  "details": {
    "fields": {
      "endAt": "debe ser mayor que startAt"
    }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Fechas faltantes, mal formadas o rango invertido. | — |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |

## Notas

- La disponibilidad se **deriva** de las reservas y no se almacena como saldo (RN-66). Las reservas canceladas no cuentan y liberan stock (RN-66, OQ-19).
- Solo se consideran las reservas activas (las que no liberan el espacio) que se solapan con la ventana (RN-65).
- La validación definitiva ocurre en `POST /reservations`, dentro de la misma transacción; esta consulta es informativa.
