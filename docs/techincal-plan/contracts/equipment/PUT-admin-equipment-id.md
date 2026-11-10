# PUT /admin/equipment/{id}

- **UC / SPEC**: UC13 — [Solicitar implementos extras](../../../features/013-solicitar-implementos-extras/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador del implemento. |

### Cuerpo

Se envían solo los campos que se quieren modificar.

| Campo | Tipo | Descripción |
|---|---|---|
| `name` | string | Nombre del implemento. |
| `discipline` | enum `Discipline` o `null` | Disciplina sugerida. |
| `stock` | integer | Existencias totales (mayor o igual a 0). |
| `status` | enum `EquipmentStatus` | `AVAILABLE` o `INACTIVE`. |

```json
{
  "stock": 12
}
```

## Respuestas

### 200 OK

```json
{
  "id": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
  "name": "Balón de voleibol",
  "discipline": "VOLLEYBALL",
  "stock": 12,
  "status": "AVAILABLE"
}
```

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "El stock no puede ser negativo.",
  "details": {
    "fields": {
      "stock": "debe ser mayor o igual a 0"
    }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Stock negativo, nombre vacío o enum inválido. | RN-62 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |
| `EQUIPMENT_NOT_FOUND` | 404 | El implemento no existe. | RN-62 |

## Notas

- Los cambios de stock rigen para las validaciones posteriores; las reservas existentes se mantienen y no se recalculan (OQ-05).
- Desactivar un implemento impide solicitarlo en reservas nuevas, pero no afecta las solicitudes existentes (OQ-05).
- La cantidad de una reserva se acota por el stock disponible en su franja, no por un límite adicional (OQ-18).
