# PUT /admin/spaces/{id}/images/{kind}

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Administrador; autenticación Bearer.

## Solicitud

`id` es el UUID del espacio y `kind` es `PREVIEW` o `PANORAMA`. La solicitud `multipart/form-data` contiene la parte obligatoria `image`. Se detecta el formato por contenido mediante los lectores ImageIO disponibles. El límite es 30 MiB (31.457.280 bytes).

El límite de ancho × alto es configurable mediante `APP_STORAGE_MAX_IMAGE_PIXELS` (64.000.000 por defecto). El backend sube el archivo original a Supabase Storage privado, sin comprimir, antes de actualizar su referencia en PostgreSQL. El objeto anterior se encola para borrado solo si se confirma la transacción; un fallo conserva la referencia anterior.

Cada dimensión admite hasta `APP_STORAGE_MAX_IMAGE_DIMENSION` (16.384 por defecto). La muestra decodificada durante la validación no sustituye al archivo original.

## Respuestas

- `204 No Content`: imagen reemplazada; la otra imagen no cambia.
- `400 VALIDATION_ERROR`: tipo, parte o contenido inválido, o carga demasiado grande.
- `401`: autenticación ausente o inválida.
- `403 FORBIDDEN`: usuario sin rol Administrador.
- `404 SPACE_NOT_FOUND`: espacio inexistente.
- `503 STORAGE_NOT_CONFIGURED`: falta configuración de acceso a Storage.
- `503 STORAGE_UNAVAILABLE`: fallo de acceso o respuesta no exitosa de Storage.

No existe una operación de eliminación de imágenes.
