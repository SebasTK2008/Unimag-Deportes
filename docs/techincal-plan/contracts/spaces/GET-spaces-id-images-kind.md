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
- `503 STORAGE_NOT_CONFIGURED`: falta configuración de acceso a Storage.
- `503 STORAGE_UNAVAILABLE`: fallo al abrir el objeto privado en Storage, incluido un archivo remoto ausente.

Cada espacio debe tener ambas imágenes; no hay una respuesta de imagen ausente para un espacio válido.

PostgreSQL contiene la referencia, no el archivo. El backend obtiene el objeto privado de Supabase y lo transmite sin redireccionar al cliente ni exponer credenciales. La respuesta incluye tamaño y `Cache-Control: private, no-store`. Si la conexión falla después de iniciar la entrega, la descarga puede quedar incompleta y ya no es posible sustituirla por un error JSON.

Un error de lectura anterior al commit conserva las cabeceras CORS y de seguridad y responde JSON `503 STORAGE_UNAVAILABLE`, sin el tamaño ni el tipo de la imagen. Para mostrarla desde React se requiere una descarga autenticada mediante `fetch`/blob; un `<img src>` convencional no añade Bearer.
