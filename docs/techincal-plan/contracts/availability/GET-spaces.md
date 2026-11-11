# GET /spaces

- **UC / SPEC**: UC02 — [Consultar disponibilidad](../../../features/002-consultar-disponibilidad/spec.md); UC03 — [Reservar espacio deportivo](../../../features/003-reservar-espacio-deportivo/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Query

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `discipline` | enum `Discipline` | No | Filtra los espacios que sirven esa disciplina. |

```http
GET /spaces?discipline=VOLLEYBALL
```

## Respuestas

### 200 OK

```json
{
  "spaces": [
    {
      "id": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "name": "Cancha de voleibol 1",
      "capacity": 12,
      "requirements": "Calzado deportivo",
      "openingTime": "06:00",
      "closingTime": "22:00",
      "status": "ACTIVE",
      "disciplines": ["VOLLEYBALL"]
    }
  ]
}
```

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La disciplina indicada no es válida.",
  "details": {
    "fields": { "discipline": "valor inválido" }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | `discipline` no pertenece al enum. | — |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |

## Notas

- Solo se listan espacios con `status: ACTIVE`; los que están en mantenimiento no aparecen como reservables (RN-09). El Administrador usa `GET /admin/spaces` para verlos todos.
- Un espacio puede servir varias disciplinas; la reserva bloquea el espacio completo (RN-07).
