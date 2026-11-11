# GET /me

- **UC / SPEC**: UC01 — [Iniciar sesión](../../../features/001-iniciar-sesion/spec.md)
- **Rol**: Usuario, Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

Sin parámetros.

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

- `suspendedUntil` es `null` si el usuario no está suspendido; si lo está, es un instante ISO-8601 (ver SPEC 010).
- `needsName` es `true` mientras falte el nombre del primer ingreso.

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. | RNF-10 |

## Notas

- `GET /me` es uno de los dos endpoints permitidos mientras el perfil esté incompleto (junto con `/auth/*`).
- El estado del usuario (existencia, rol y suspensión) se lee de la base de datos en cada petición (RN-58).
