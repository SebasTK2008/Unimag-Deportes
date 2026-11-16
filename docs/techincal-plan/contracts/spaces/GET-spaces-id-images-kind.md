# GET /spaces/{id}/images/{kind}

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Usuario o Administrador autenticado mediante Bearer.

## Solicitud

`id` es el UUID del espacio y `kind` es `PREVIEW` o `PANORAMA`.

## Respuestas

- `200 OK`: contenido binario con el `Content-Type` detectado al cargarlo.
- `400 VALIDATION_ERROR`: identificador o tipo inválido.
- `401`: autenticación ausente o inválida.
- `404 SPACE_NOT_FOUND`: espacio inexistente.

Cada espacio debe tener ambas imágenes; no hay una respuesta de imagen ausente para un espacio válido.
