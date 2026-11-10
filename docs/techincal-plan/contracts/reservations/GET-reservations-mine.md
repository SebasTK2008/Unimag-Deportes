# GET /reservations/mine

- **UC / SPEC**: UC04 — [Gestionar mis reservas](../../../features/004-gestionar-mis-reservas/spec.md)
- **Rol**: Usuario
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

Sin parámetros.

## Respuestas

### 200 OK

```json
{
  "reservations": [
    {
      "id": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "space": {
        "id": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
        "name": "Cancha de voleibol 1"
      },
      "startAt": "2026-11-11T14:00:00-05:00",
      "endAt": "2026-11-11T16:00:00-05:00",
      "status": "CONFIRMED",
      "createdAt": "2026-11-10T18:20:00-05:00",
      "checkedInAt": null,
      "equipment": [
        {
          "equipmentId": "9f8e7d6c-5b4a-4938-8271-6a5b4c3d2e1f",
          "name": "Balón de voleibol",
          "quantity": 1
        }
      ]
    }
  ]
}
```

- Incluye reservas próximas y pasadas, en todos los estados (RN-05, sección 6 del contexto).
- `equipment` refleja lo solicitado; si no hay, es un arreglo vacío.

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |
| `PROFILE_INCOMPLETE` | 403 | Falta el nombre del primer ingreso. | RN-59 |

## Notas

- Solo se devuelven las reservas del usuario del token; las reservas son nominales (RN-03).
- Un usuario suspendido puede consultar sus reservas (RN-05).
- Desde cada reserva el cliente puede ofrecer la cancelación (`GET /reservations/{id}/cancellation-preview`).
