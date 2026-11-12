# POST /contact

- **UC / SPEC**: UC12 — [Contactar administrador](../../../features/012-contactar-administrador/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Cuerpo

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `subject` | string | Sí | Asunto del mensaje; no admite retornos de carro ni saltos de línea. |
| `message` | string | Sí | Contenido del mensaje para el área de deportes. |

```json
{
  "subject": "Problema con una reserva",
  "message": "No pude hacer check-in en la cancha de voleibol el 11 de noviembre."
}
```

## Respuestas

### 202 Accepted

```json
{
  "message": "Tu mensaje fue enviado al área de deportes."
}
```

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "El asunto y el mensaje son obligatorios.",
  "details": {
    "fields": {
      "subject": "no puede estar vacío",
      "message": "no puede estar vacío"
    }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Falta el asunto o el mensaje, o el asunto contiene un retorno de carro o salto de línea. | — |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `PROFILE_INCOMPLETE` | 403 | Falta el nombre del primer ingreso. | RN-59 |
| `CONTACT_NOT_CONFIGURED` | 503 | El destinatario del área de deportes no está configurado. | — |
| `MAIL_DELIVERY_FAILED` | 503 | No se pudo enviar el mensaje. | — |

## Notas

- El mensaje no se persiste: se envía por correo al área de deportes (OQ-10).
- Este formulario es el canal de contacto que enlazan los correos de la plataforma (RN-48, OQ-10).
