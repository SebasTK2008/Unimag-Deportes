# PUT /me/profile

- **UC / SPEC**: UC01 — [Iniciar sesión](../../../features/001-iniciar-sesion/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `name` | string | Sí | Nombre de la persona, necesario para el comprobante (RN-45, RN-59). |

```json
{
  "name": "Juan Pérez"
}
```

## Respuestas

### 200 OK

```json
{
  "id": "7c1e5a2b-3f4d-4a6e-9b8c-1d2e3f4a5b6c",
  "email": "jperez@unimagdalena.edu.co",
  "name": "Juan Pérez",
  "role": "USER",
  "suspendedUntil": null,
  "needsName": false
}
```

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "El nombre es obligatorio.",
  "details": {
    "fields": {
      "name": "no puede estar vacío"
    }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Falta el nombre o está vacío. | RN-59 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |

## Notas

- Se pide una sola vez, en el primer ingreso. Mientras falte, el resto de endpoints responde `403 PROFILE_INCOMPLETE`.
- Con Microsoft (Entra ID) el nombre vendría en el token y este paso desaparecería (§7.1.5 del plan).
