# GET /equipment

- **UC / SPEC**: UC13 — [Solicitar implementos extras](../../../features/013-solicitar-implementos-extras/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Query

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `discipline` | enum `Discipline` | No | Filtra por la disciplina sugerida del implemento. |

```http
GET /equipment?discipline=VOLLEYBALL
```

## Respuestas

### 200 OK

```json
{
  "equipment": [
    {
      "id": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
      "name": "Balón de voleibol",
      "discipline": "VOLLEYBALL",
      "stock": 10,
      "status": "AVAILABLE"
    },
    {
      "id": "8e7d6c5b-4a39-4827-9160-5a4b3c2d1e0f",
      "name": "Malla de voleibol",
      "discipline": "VOLLEYBALL",
      "stock": 2,
      "status": "INACTIVE"
    }
  ]
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | `discipline` no pertenece al enum. | — |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |

## Notas

- Devuelve el catálogo completo, incluidos los inactivos, para que el cliente pueda mostrarlos o filtrarlos.
- Solo los implementos con `status: AVAILABLE` y stock suficiente se pueden solicitar (RN-62).
- El stock es el total del catálogo; la disponibilidad por franja se consulta en `GET /equipment/availability`.
