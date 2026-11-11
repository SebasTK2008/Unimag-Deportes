# POST /admin/spaces

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `name` | string | Sí | Nombre del espacio. |
| `capacity` | integer | Sí | Aforo máximo. |
| `requirements` | string | No | Requisitos de uso. |
| `openingTime` | `HH:mm` | Sí | Inicio del horario de operación. |
| `closingTime` | `HH:mm` | Sí | Fin del horario de operación. |
| `status` | enum `SpaceStatus` | No | Por defecto `ACTIVE`. |
| `disciplines` | array `Discipline` | Sí | Al menos una disciplina. |

```json
{
  "name": "Cancha de voleibol 1",
  "capacity": 12,
  "requirements": "Calzado deportivo",
  "openingTime": "06:00",
  "closingTime": "22:00",
  "status": "ACTIVE",
  "disciplines": ["VOLLEYBALL"]
}
```

## Respuestas

### 201 Created

```json
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
| `VALIDATION_ERROR` | 400 | Campos faltantes, disciplinas vacías u horario inválido. | RN-06, RN-08 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- Las franjas de 60 minutos se derivan del horario de operación (RN-08).
- Un espacio nuevo puede quedar en `ACTIVE` o directamente en `MAINTENANCE`.
