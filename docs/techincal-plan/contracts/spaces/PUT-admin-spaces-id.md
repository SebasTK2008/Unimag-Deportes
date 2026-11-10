# PUT /admin/spaces/{id}

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador del espacio. |

### Cuerpo

Se envían solo los campos que se quieren modificar.

| Campo | Tipo | Descripción |
|---|---|---|
| `name` | string | Nombre del espacio. |
| `capacity` | integer | Aforo máximo. |
| `requirements` | string | Requisitos de uso. |
| `openingTime` | `HH:mm` | Inicio del horario de operación. |
| `closingTime` | `HH:mm` | Fin del horario de operación. |
| `status` | enum `SpaceStatus` | `ACTIVE` o `MAINTENANCE`. |
| `disciplines` | array `Discipline` | Disciplinas que sirve el espacio. |

```json
{
  "status": "MAINTENANCE"
}
```

## Respuestas

### 200 OK

```json
{
  "id": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "name": "Cancha de voleibol 1",
  "capacity": 12,
  "requirements": "Calzado deportivo",
  "openingTime": "06:00",
  "closingTime": "22:00",
  "status": "MAINTENANCE",
  "disciplines": ["VOLLEYBALL"]
}
```

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "El horario de cierre debe ser posterior al de apertura.",
  "details": {
    "fields": {
      "closingTime": "debe ser mayor que openingTime"
    }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Valores inválidos o combinación de horario incoherente. | RN-06, RN-08 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |
| `SPACE_NOT_FOUND` | 404 | El espacio no existe. | — |

## Notas

- "Desactivar" un espacio equivale a ponerlo en `MAINTENANCE` (OQ-12).
- Los cambios rigen solo para operaciones nuevas: las reservas existentes se mantienen y no se cancelan (OQ-05).
- Un espacio en mantenimiento no aparece como reservable (RN-09).
