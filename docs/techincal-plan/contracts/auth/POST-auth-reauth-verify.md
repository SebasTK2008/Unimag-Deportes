# POST /auth/reauth/verify

- **UC / SPEC**: UC01 — [Iniciar sesión](../../../features/001-iniciar-sesion/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `code` | string | Sí | Código de reautenticación de 6 dígitos. |

```json
{
  "code": "731904"
}
```

## Respuestas

### 200 OK

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresAt": "2026-11-11T08:30:00-05:00",
  "reauthAt": "2026-11-11T07:45:00-05:00"
}
```

- El token nuevo conserva el mismo `exp` del token presentado: la reautenticación no extiende la sesión (RN-56, D-21).
- `reauthAt` habilita las acciones sensibles durante los 10 minutos siguientes.

### 400 Bad Request

```json
{
  "code": "INVALID_CODE",
  "message": "El código no es correcto.",
  "details": {
    "attemptsRemaining": 3
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Falta `code` o el formato no es válido. | — |
| `INVALID_CODE` | 400 | El código no coincide. | RN-51 |
| `CODE_EXPIRED` | 400 | Pasaron más de 10 minutos. | RN-51 |
| `CODE_INVALIDATED` | 400 | El código ya se usó o fue reemplazado. | RN-51 |
| `TOO_MANY_ATTEMPTS` | 400 | Se superaron 5 intentos fallidos. | RN-52 |
| `TOKEN_EXPIRED` | 401 | El token presentado venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |

## Notas

- El cliente reemplaza su token por el devuelto y conserva la pantalla actual.
- `reauthAt` no se usa para autorizar operaciones de negocio distintas de las acciones sensibles.
