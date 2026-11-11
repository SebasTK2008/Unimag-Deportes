# Unimag-Deportes
Plataforma de reservas de espacios deportivos en la universidad del magdalena

## Desarrollo local (core parcial)

El backend requiere Java 25, PostgreSQL y una clave JWT externa de al menos 32 bytes. Configure `APP_AUTH_JWT_SECRET` (propiedad `app.auth.jwt.secret`) y `SPRING_PROFILES_ACTIVE=dev` en el entorno; no guarde claves reales en el repositorio. `compose.yaml` define PostgreSQL y Mailpit, y el proyecto incluye integración de Docker Compose para desarrollo. El arranque completo con Docker está pendiente de verificación.

Con el perfil `dev`, `POST /dev/login` acepta JSON con `email` institucional y `name`, crea el usuario si hace falta y devuelve un JWT en `token`. Este acceso no verifica el control del correo y no existe fuera del perfil `dev`; no sustituye el flujo por código de UC01, que está pendiente. La lista opcional `app.auth.admin-emails` determina el rol inicial de los usuarios nuevos.

La CSP configurada en la API restringe sus propias respuestas. La aplicación React se distribuye desde otro origen y su servidor debe proporcionar una CSP apropiada por separado.

La migración única `src/main/resources/db/migration/V1__shared_schema.sql` es editable durante el desarrollo. Cambiarla después de aplicarla provoca un conflicto de checksum en Flyway: coordine la recreación de las bases locales o de prueba, sin borrar datos que deban conservarse.
