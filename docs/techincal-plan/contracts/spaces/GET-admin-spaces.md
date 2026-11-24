# GET /admin/spaces

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

Sin parámetros.

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
    },
    {
      "id": "4c3b2a1d-6f7e-4b8c-9d0e-1f2a3b4c5d6e",
      "name": "Cancha de fútbol 5",
      "capacity": 10,
      "requirements": "Calzado deportivo",
      "openingTime": "06:00",
      "closingTime": "20:00",
      "status": "MAINTENANCE",
      "disciplines": ["FOOTBALL_5"]
    }
  ]
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- A diferencia de `GET /spaces`, incluye los espacios en `MAINTENANCE` (RN-06, RN-09).
- Los atributos de cada espacio son los de RN-06: nombre, disciplina(s), aforo, requisitos, horario y estado.
