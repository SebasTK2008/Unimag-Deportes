# GET /admin/blocks

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md), HU3.
- **Rol**: cualquier `ADMIN` con perfil completo y Bearer válido. La lectura no requiere reautenticación.
- **Estado de entrega**: implementado conforme a D-27; evidencia de pruebas en el plan UC07.

## Solicitud

| Parámetro | Obligatorio | Regla |
|---|---|---|
| `spaceId` | Sí | UUID de espacio existente. |
| `from`, `to` | Sí | Instantes ISO-8601 con offset; `from < to`. Selección por solape `[from, to)`: `startAt < to` y `endAt > from`. Sin horizonte público de siete fechas. |
| `status` | No | `ACTIVE` o `CANCELLED`. Omitir para ambos estados. `ACTIVE` significa no cancelado, incluso si ya terminó. |
| `page` | No | Base cero, predeterminado `0`, no negativo. |
| `size` | No | Predeterminado `20`, entre `1` y `100`. |

Orden fijo ascendente por `startAt`, después `id`. El calendario público no incorpora identificadores de bloqueos; este listado permite seleccionar filas existentes y nuevas.

## 200 OK

```json
{
  "items": [{
    "id": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "spaceId": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "type": "CLASS",
    "reason": "Clase de voleibol",
    "startAt": "2026-11-26T08:00:00-05:00",
    "endAt": "2026-11-26T09:00:00-05:00",
    "recurrenceGroup": null,
    "status": "CANCELLED",
    "cancelledAt": "2026-11-25T10:00:00-05:00",
    "cancelledBy": "4b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "cancellationReason": "La clase fue cancelada"
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

Los tres metadatos de cancelación son `null` para `ACTIVE`. Los datos originales y `recurrenceGroup` se conservan. Esta consulta muestra el estado administrativo actual y su cancelación; no reconstruye la ocupación como se veía en un instante pasado.

## Errores

- `400 VALIDATION_ERROR`: filtros ausentes, UUID/fecha/enum inválidos, rango o paginación inválidos.
- `401 TOKEN_INVALID` / `TOKEN_EXPIRED`, `403 FORBIDDEN` / `PROFILE_INCOMPLETE`.
- `404 SPACE_NOT_FOUND`.
