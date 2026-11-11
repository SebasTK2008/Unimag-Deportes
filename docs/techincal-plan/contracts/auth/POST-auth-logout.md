# POST /auth/logout

- **UC / SPEC**: UC01 — [Iniciar sesión](../../../features/001-iniciar-sesion/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

Sin cuerpo.

## Respuestas

### 204 No Content

Sin cuerpo.

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |

## Notas

- El servidor no guarda sesiones: este endpoint solo registra el cierre en auditoría (RN-61).
- El cliente debe eliminar el token de su almacenamiento; el token no se revoca y sigue siendo válido hasta que expire (OQ-17).
- No existe "cerrar todas las sesiones" en el prototipo (OQ-17).
