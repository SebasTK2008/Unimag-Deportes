# POST /auth/reauth/code

- **UC / SPEC**: UC01 — [Iniciar sesión](../../../features/001-iniciar-sesion/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

Sin cuerpo. El código se envía al correo institucional del Administrador autenticado.

## Respuestas

### 202 Accepted

```json
{
  "message": "Se envió un código de reautenticación a tu correo institucional."
}
```

### 403 Forbidden

```json
{
  "code": "FORBIDDEN",
  "message": "Solo un Administrador puede reautenticarse.",
  "details": {}
}
```

### 429 Too Many Requests

```json
{
  "code": "RESEND_TOO_SOON",
  "message": "Debes esperar 60 segundos antes de solicitar otro código.",
  "details": {
    "retryAfterSeconds": 42
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |
| `RESEND_TOO_SOON` | 429 | Menos de 60 segundos desde la solicitud anterior. | RN-53 |
| `CODE_REQUEST_LIMIT` | 429 | Más de 5 solicitudes por hora. | RN-53 |

## Notas

- La reautenticación es necesaria para acciones sensibles: `POST /admin/blocks` y `PUT /admin/parameters` (D-21).
- El código sigue las mismas reglas que el de inicio de sesión (6 dígitos, 10 minutos, 5 intentos).
