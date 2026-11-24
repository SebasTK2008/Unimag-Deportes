# GET /admin/spaces/{id}/qr

- **UC / SPEC**: UC05 — [Confirmar asistencia (QR)](../../../features/005-confirmar-asistencia-qr/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Path

| Parámetro | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador del espacio. |

```http
GET /admin/spaces/3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/qr
```

## Respuestas

### 200 OK

```http
Content-Type: image/svg+xml; charset=UTF-8
```

Cuerpo: texto SVG UTF-8 generado a partir del QR con ZXing, para que el frontend lo renderice o descargue e imprima.

El QR contiene una URL absoluta del frontend cuya ruta es `/checkin/{id_espacio}`, sin datos del usuario (RN-31, RN-32). El origen público se configura por despliegue con `APP_CHECKIN_FRONTEND_ORIGIN` (`app.checkin.frontend-origin`). Debe incluir host y usar HTTPS; HTTP solo se admite con el perfil `dev` o `test` activo. No se admiten credenciales, consulta, fragmento ni rutas distintas de `/`.

### 404 Not Found

```json
{
  "code": "SPACE_NOT_FOUND",
  "message": "El espacio no existe.",
  "details": {}
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |
| `SPACE_NOT_FOUND` | 404 | El espacio no existe. | — |
| `QR_NOT_CONFIGURED` | 503 | El origen del frontend está vacío o no cumple las restricciones indicadas. | — |

## Notas

- El QR es estático por espacio: generarlo de nuevo produce el mismo enlace (RN-31).
- El cliente puede mostrarlo en pantalla o descargarlo para imprimir.
