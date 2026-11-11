# POST /auth/verify

- **UC / SPEC**: UC01 — [Iniciar sesión](../../../features/001-iniciar-sesion/spec.md)
- **Rol**: Público (con límite de intentos)
- **Autenticación**: no requiere

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `email` | string | Sí | Correo institucional con el que se solicitó el código. |
| `code` | string | Sí | Código de 6 dígitos recibido por correo. |

```json
{
  "email": "jperez@unimagdalena.edu.co",
  "code": "482913"
}
```

## Respuestas

### 200 OK

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresAt": "2026-11-11T08:30:00-05:00",
  "needsName": false
}
```

- `needsName: true` indica que es el primer ingreso y el cliente debe pedir el nombre con `PUT /me/profile` (RN-59).
- El token se devuelve en el cuerpo; **no** se envía `Set-Cookie` (RN-55).

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

```json
{
  "code": "CODE_EXPIRED",
  "message": "El código venció. Solicita uno nuevo.",
  "details": {}
}
```

```json
{
  "code": "TOO_MANY_ATTEMPTS",
  "message": "Superaste los 5 intentos. Solicita un código nuevo.",
  "details": {}
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Falta `email` o `code`, o el formato no es válido. | — |
| `INVALID_CODE` | 400 | El código no coincide. | RN-51 |
| `CODE_EXPIRED` | 400 | Venció la vigencia configurada (15 minutos por defecto). | RN-51 |
| `CODE_INVALIDATED` | 400 | El código ya se usó o fue reemplazado. | RN-51 |
| `TOO_MANY_ATTEMPTS` | 400 | El último intento fallido permitido invalida el código. | RN-52 |

## Notas

- Al verificar un código correcto se emite un JWT HS256 con vigencia de 24 horas, sin renovación automática (RN-54, RN-56).
- El token incluye `sub`, `email`, `role`, `iat` y `exp`; el rol es informativo y el servidor consulta el estado actual en cada petición (RN-58).
