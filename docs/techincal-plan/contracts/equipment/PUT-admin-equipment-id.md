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

Se envían solo los campos que se quieren modificar. Los campos omitidos conservan su valor; `discipline: null` elimina la disciplina sugerida. `name`, `stock` y `status` no admiten `null` cuando se envían.

| Campo | Tipo | Descripción |
|---|---|---|
| `name` | string | Nombre no vacío de hasta 200 caracteres; único en el catálogo. |
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
  "message": "La solicitud contiene campos inválidos.",
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
| `EQUIPMENT_NAME_ALREADY_EXISTS` | 409 | Otro implemento ya tiene ese nombre. | RN-62 |
| `STOCK_BELOW_COMMITTED` | 409 | El stock propuesto es inferior al máximo compromiso simultáneo futuro. | RN-65 |

## Notas

- Se rechaza toda reducción por debajo del máximo compromiso simultáneo futuro. La edición y las reservas usan el mismo bloqueo del implemento; las reservas existentes se conservan.
- El cálculo futuro incluye la parte pendiente de las reservas ya iniciadas que todavía no terminaron. `stock` debe ser un entero JSON de 32 bits; se rechazan decimales, cadenas numéricas y valores fuera de rango.
- Desactivar un implemento impide solicitarlo en reservas nuevas, pero no afecta las solicitudes existentes (OQ-05).
- La cantidad de una reserva se acota por el stock disponible en su franja, no por un límite adicional (OQ-18).
