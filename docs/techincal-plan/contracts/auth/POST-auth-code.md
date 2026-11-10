# POST /auth/code

- **UC / SPEC**: UC01 — [Iniciar sesión](../../../features/001-iniciar-sesion/spec.md)
- **Rol**: Público (con límite de solicitudes)
- **Autenticación**: no requiere

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `email` | string | Sí | Correo institucional completo. El servidor valida el dominio contra el parámetro configurado. |

```json
{
  "email": "jperez@unimagdalena.edu.co"
}
```

## Respuestas

### 202 Accepted

La respuesta es la misma exista o no una cuenta previa (RN-50).

```json
{
  "message": "Si el correo pertenece al dominio institucional, recibirás un código de acceso."
}
```

### 400 Bad Request

```json
{
  "code": "INVALID_EMAIL_DOMAIN",
  "message": "El correo debe pertenecer al dominio institucional.",
  "details": {
    "field": "email"
  }
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

```json
{
  "code": "CODE_REQUEST_LIMIT",
  "message": "Alcanzaste el máximo de 5 solicitudes por hora para este correo.",
  "details": {
    "retryAfterSeconds": 1800
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Falta `email` o tiene formato inválido. | — |
| `INVALID_EMAIL_DOMAIN` | 400 | El dominio no es el institucional configurado. | RN-50 |
| `RESEND_TOO_SOON` | 429 | Menos de 60 segundos desde la solicitud anterior. | RN-53 |
| `CODE_REQUEST_LIMIT` | 429 | Más de 5 solicitudes por hora para el correo. | RN-53 |
| `MAIL_DELIVERY_FAILED` | 503 | Falló el envío del código; se informa sin revelar si la cuenta existe y se permite solicitar otro sujeto a los límites. | OQ-04 |

## Notas

- El código tiene 6 dígitos, vence a los 15 minutos por defecto (configurable por despliegue) y es de un solo uso; solicitar uno nuevo invalida el anterior (RN-51).
- Se aplica además un máximo fijo de 30 solicitudes por IP en una ventana rodante de una hora.
- El servidor genera el código con un generador seguro y guarda solo su hash.
- Si el correo no pertenece al dominio, no se envía nada.
- El envío del correo es la única excepción a "un fallo de correo no revierte la operación": si falla, se informa y se permite reintentar (OQ-04).
