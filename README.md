# Unimag-Deportes
Plataforma de reservas de espacios deportivos en la universidad del magdalena

## Desarrollo local

El backend requiere Java 25, PostgreSQL y dos claves externas independientes de al menos 32 bytes: `APP_AUTH_JWT_SECRET` y `APP_AUTH_CODE_HMAC_SECRET`. No guarde claves reales en el repositorio. `compose.yaml` define PostgreSQL y Mailpit, y el proyecto incluye integración de Docker Compose para desarrollo. Se verificó el JAR con esos servicios: solicitud de código, entrega SMTP, login, perfil y logout.

El flujo de UC01 usa `POST /auth/code` y `POST /auth/verify`; luego se completa el nombre mediante `PUT /me/profile` si `needsName` es `true`. Consulte los [contratos de autenticación](docs/techincal-plan/contracts/README.md#autenticación-uc01). El token dura 24 horas y se envía como `Authorization: Bearer`, nunca en cookies. El cliente React, en otro repositorio, conserva el destino de navegación y elimina su token al cerrar sesión.

Con el perfil opcional `dev`, `POST /dev/login` acepta JSON con `email` institucional y `name`, crea el usuario si hace falta y devuelve un JWT en `token`. Este acceso no verifica el control del correo y no existe fuera del perfil `dev`; no debe habilitarse en producción. `APP_AUTH_ADMIN_EMAILS` determina únicamente el rol inicial de los usuarios nuevos.

### Configuración

El repositorio incluye `.env.example` como plantilla local; `.env` y sus variantes están excluidos de Git. El archivo no se carga automáticamente. Para usarlo, cópielo, complete las claves obligatorias y configure explícitamente la importación de Spring desde la raíz del proyecto:

```powershell
Copy-Item .env.example .env
# Edite .env: establezca claves independientes y los valores necesarios.
$env:SPRING_CONFIG_IMPORT = 'file:.env[.properties]'
.\mvnw.cmd spring-boot:run
```

En macOS/Linux, use `cp .env.example .env` y `SPRING_CONFIG_IMPORT='file:.env[.properties]' ./mvnw spring-boot:run`. Las variables del entorno tienen prioridad sobre el archivo importado. No incluya el prefijo `export` ni comillas de shell en `.env`: se interpreta como un archivo de propiedades de Spring. No active el perfil `dev` en producción; si necesita HTTP para el frontend local, actívelo explícitamente para desarrollo.

Para desplegar el JAR, inyecte las variables directamente mediante el entorno o el gestor de secretos de la plataforma; `.env.example` no es una configuración de producción. Configure PostgreSQL y SMTP externos, los orígenes públicos y credenciales apropiados, y no incluya `.env` ni secretos en la imagen o repositorio. `compose.yaml` solo define PostgreSQL y Mailpit para desarrollo; no despliega el backend.

| Variable | Uso / valor predeterminado |
|---|---|
| `APP_AUTH_JWT_SECRET` | Obligatoria; clave HS256 de al menos 32 bytes UTF-8. |
| `APP_AUTH_CODE_HMAC_SECRET` | Obligatoria; clave HMAC de al menos 32 bytes UTF-8, distinta de la clave JWT. |
| `APP_AUTH_CODE_VALIDITY` | Duración ISO-8601 positiva; `PT15M`. |
| `APP_AUTH_CODE_MAX_ATTEMPTS` | Entero positivo; `5`. |
| `APP_AUTH_ADMIN_EMAILS` | Correos separados por comas; vacío por defecto. |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes del cliente separados por comas; vacío por defecto. No usa credenciales de cookies. |
| `APP_CHECKIN_FRONTEND_ORIGIN` | Origen público del frontend para los QR de UC05; vacío por defecto. Requiere HTTPS, salvo HTTP en perfiles `dev` o `test`; sin un origen válido, el endpoint QR responde `503 QR_NOT_CONFIGURED`. |
| `MAIL_HOST`, `MAIL_PORT` | SMTP; `localhost`, `1025` (Mailpit). |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Credenciales SMTP opcionales. |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | `false` en local; configurar según el proveedor SMTP. |
| `APP_MAIL_CONTACT_URL` | URL del formulario de contacto del frontend; pendiente de definir para despliegue. Si está vacía, el código de acceso no se envía y la solicitud responde `503 MAIL_DELIVERY_FAILED`; tampoco se envían comprobantes ni avisos. |
| `APP_MAIL_CONTACT_RECIPIENT` | Correo del área de deportes, pendiente de definir para despliegue. Sin él, `POST /contact` responde `503 CONTACT_NOT_CONFIGURED`. |
| `APP_MAIL_CANCELLATION_URL` | URL del flujo de cancelación del frontend con `{id}` como identificador de la reserva; pendiente de definir para despliegue. Sin ella no se envía el recordatorio. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión externa si no se usa la detección automática de Compose. |

Con las claves ya configuradas en el entorno y Docker disponible, ejecute `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`). Mailpit expone su buzón local en `http://localhost:8025`. Para ejecutar el JAR fuera de la integración automática de Compose, configure también las variables de conexión a PostgreSQL y el servidor SMTP.

`./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`) ejecuta las pruebas con PostgreSQL y Mailpit mediante Testcontainers; las claves ficticias de pruebas no requieren configurar secretos reales. Incluye pruebas de contrato de los endpoints, límites, concurrencia, rollback, expiración, estado vigente y seguridad. La última verificación terminó con 214 pruebas sin fallos.

Para las pruebas no es necesario crear ni importar `.env`: Testcontainers y las configuraciones de prueba proporcionan sus propios valores. Ejecútelas en una consola sin `SPRING_CONFIG_IMPORT` ni variables de producción heredadas; por ejemplo, en PowerShell use `Remove-Item Env:SPRING_CONFIG_IMPORT -ErrorAction SilentlyContinue` antes de `.\mvnw.cmd clean verify`. Docker debe ser accesible para Testcontainers.

La CSP configurada en la API restringe sus propias respuestas. La aplicación React se distribuye desde otro origen y su servidor debe proporcionar una CSP apropiada por separado.

En producción se requiere HTTPS y HSTS en el punto de entrada. Por defecto no se confía en encabezados reenviados: el límite por IP usa la dirección remota de la conexión. Si existe un proxy, configure explícitamente su confianza y evite que la API sea accesible directamente; no habilite la confianza indiscriminada en `X-Forwarded-For`. No active logs de cuerpos HTTP, credenciales SMTP o tokens.

La migración única `src/main/resources/db/migration/V1__shared_schema.sql` es editable durante el desarrollo. Cambiarla después de aplicarla provoca un conflicto de checksum en Flyway: coordine la recreación de las bases locales o de prueba, sin borrar datos que deban conservarse. El último cambio de este tipo añadió el índice `audit_log_action_created_idx` para los indicadores de UC09; las bases creadas antes deben recrearse.

### Versionado de reglas (UC06)

El diseño D-26 conserva versiones inmutables de cuota y umbral en `usage_parameters`; `usage_parameters_head` contiene únicamente la referencia vigente. Cada cambio efectivo crea una instantánea con número, UUID, fecha y administrador, y una auditoría `PARAMETERS_UPDATED` vinculada a esa versión. Las solicitudes sin cambios no crean otra versión. Los tres contratos REST de UC06 mantienen su forma; el historial se consulta en base de datos, sin endpoint adicional.

Esta extensión reescribe la V1 existente: no hay V2 ni actualización automática de bases creadas con el esquema anterior. La instalación nueva inicia en versión 1 con cuota 4, umbral 3 y autor nulo. La fecha inicial indica cuándo se instaló el esquema. Antes de recrear una base anterior, respalde y coordine la conservación de los datos necesarios; no basta con reparar el checksum, porque también cambia la estructura.
