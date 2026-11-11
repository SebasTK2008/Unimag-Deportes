# POST /admin/equipment

- **UC / SPEC**: UC13 — [Solicitar implementos extras](../../../features/013-solicitar-implementos-extras/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `name` | string | Sí | Nombre del implemento. |
| `discipline` | enum `Discipline` | No | Disciplina sugerida; puede ser `null`. |
| `stock` | integer | Sí | Existencias totales (mayor o igual a 0). |
| `status` | enum `EquipmentStatus` | No | Por defecto `AVAILABLE`. |

```json
{
  "name": "Balón de fútbol 5",
  "discipline": "FOOTBALL_5",
  "stock": 8,
  "status": "AVAILABLE"
}
```

## Respuestas

### 201 Created

```json
{
  "id": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
  "name": "Balón de fútbol 5",
  "discipline": "FOOTBALL_5",
  "stock": 8,
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
| `VALIDATION_ERROR` | 400 | Falta el nombre, stock negativo o enum inválido. | RN-62 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- El nombre es único en el catálogo.
- La disciplina es solo una sugerencia para filtrar; no restringe en qué espacio se puede solicitar (OQ-21).
- Un implemento inactivo no se puede solicitar en reservas nuevas (RN-62).
