# PUT /admin/spaces/{id}/images/{kind}

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Administrador; autenticación Bearer.

## Solicitud

`id` es el UUID del espacio y `kind` es `PREVIEW` o `PANORAMA`. La solicitud `multipart/form-data` contiene la parte obligatoria `image`. Se detecta el formato por contenido mediante los lectores ImageIO disponibles. El límite es 30 MiB (31.457.280 bytes).

## Respuestas

- `204 No Content`: imagen reemplazada; la otra imagen no cambia.
- `400 VALIDATION_ERROR`: tipo, parte o contenido inválido, o carga demasiado grande.
- `401`: autenticación ausente o inválida.
- `403 FORBIDDEN`: usuario sin rol Administrador.
- `404 SPACE_NOT_FOUND`: espacio inexistente.

No existe una operación de eliminación de imágenes.
