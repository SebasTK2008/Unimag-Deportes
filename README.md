# Unimag-Deportes
Plataforma de reservas de espacios deportivos en la universidad del magdalena

## Desarrollo local

El backend usa Spring Boot 4.2.0 y requiere Java 25, PostgreSQL y dos claves externas independientes de al menos 32 bytes: `APP_AUTH_JWT_SECRET` y `APP_AUTH_CODE_HMAC_SECRET`. No guarde claves reales en el repositorio. `compose.yaml` define PostgreSQL y Mailpit, y el proyecto incluye integración de Docker Compose para desarrollo. Se verificó el JAR con esos servicios: solicitud de código, entrega SMTP, login, perfil y logout.

El flujo de UC01 usa `POST /auth/code` y `POST /auth/verify`; luego se completa el nombre mediante `PUT /me/profile` si `needsName` es `true`. Consulte los [contratos de autenticación](docs/techincal-plan/contracts/README.md#autenticación-uc01). El token dura 24 horas y se envía como `Authorization: Bearer`, nunca en cookies. El cliente React, en otro repositorio, conserva el destino de navegación y elimina su token al cerrar sesión.

Con el perfil opcional `dev`, `POST /dev/login` acepta JSON con `email` institucional y `name`, crea el usuario si hace falta y devuelve un JWT en `token`. Este acceso no verifica el control del correo y no existe fuera del perfil `dev`; no debe habilitarse en producción. `APP_AUTH_ADMIN_EMAILS` determina únicamente el rol inicial de los usuarios nuevos.

### Configuración

El repositorio incluye `.env.example` como plantilla local para un cliente futuro en `http://localhost:5173`; `.env` y sus variantes están excluidos de Git. El archivo no se carga automáticamente. Para usarlo, cópielo si no existe, complete las claves obligatorias y configure explícitamente la importación de Spring desde la raíz del proyecto:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Edite .env: establezca claves independientes y los valores necesarios.
$env:SPRING_CONFIG_IMPORT = 'file:.env[.properties]'
.\mvnw.cmd spring-boot:run
```

En macOS/Linux, use `cp .env.example .env` y `SPRING_CONFIG_IMPORT='file:.env[.properties]' ./mvnw spring-boot:run`. Las variables del entorno tienen prioridad sobre el archivo importado. No incluya el prefijo `export` ni comillas de shell en `.env`: se interpreta como un archivo de propiedades de Spring. No active el perfil `dev` en producción; si necesita HTTP para el frontend local, actívelo explícitamente para desarrollo.

Para desplegar el JAR, inyecte las variables directamente mediante el entorno o el gestor de secretos de la plataforma; `.env.example` no es una configuración de producción. Configure PostgreSQL y SMTP externos, los orígenes públicos y credenciales apropiados, y no incluya `.env` ni secretos en la imagen o repositorio. Por defecto, `compose.yaml` inicia PostgreSQL y Mailpit para desarrollo; el perfil opcional `app` incluye también el backend. No es una configuración de producción.

| Variable | Uso / valor predeterminado |
|---|---|
| `APP_AUTH_JWT_SECRET` | Obligatoria; clave HS256 de al menos 32 bytes UTF-8. |
| `APP_AUTH_CODE_HMAC_SECRET` | Obligatoria; clave HMAC de al menos 32 bytes UTF-8, distinta de la clave JWT. |
| `APP_AUTH_CODE_VALIDITY` | Duración ISO-8601 positiva; `PT15M`. |
| `APP_AUTH_CODE_MAX_ATTEMPTS` | Entero positivo; `5`. |
| `APP_AUTH_CODE_RESEND_INTERVAL` | Duración ISO-8601 positiva entre solicitudes de un correo; `PT1M`. |
| `APP_AUTH_CODE_MAX_REQUESTS_PER_EMAIL` | Entero positivo, solicitudes por hora rodante por correo; `5`. El límite por IP permanece fijo en 30/h. |
| `APP_AUTH_ADMIN_EMAILS` | Correos separados por comas; vacío por defecto. |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes del cliente separados por comas; vacío por defecto. No usa credenciales de cookies. |
| `SERVER_PORT` | Puerto de Spring al ejecutar con Maven/JAR; `8080`. En Compose, `APP_PORT` publica el puerto del host y el contenedor sigue escuchando en `8080`. |
| `APP_STORAGE_SUPABASE_URL` | Origen HTTPS del proyecto Supabase; vacío por defecto. |
| `APP_STORAGE_SUPABASE_BUCKET` | Bucket privado de imágenes; `space-images`. |
| `APP_STORAGE_SUPABASE_KEY` | Clave exclusiva del servidor: secreta `sb_secret_...` o JWT heredado `service_role`. Sin clave u origen, las operaciones de Storage responden `503 STORAGE_NOT_CONFIGURED`. Nunca exponer al cliente. |
| `APP_STORAGE_SUPABASE_TIMEOUT` | Duración ISO-8601 de al menos 1 ms y menor que una hora; `PT1M`. Plazo total de subida/borrado; conexión y espera de lectura para descargas. |
| `APP_STORAGE_MAX_IMAGE_PIXELS` | Máximo positivo de ancho × alto antes de decodificar; `64000000`. No redimensiona ni comprime. |
| `APP_STORAGE_MAX_IMAGE_DIMENSION` | Máximo positivo de cada dimensión; `16384`. Protege también frente a imágenes extremadamente estrechas o altas. |
| `PORT` | Puerto HTTP; `8080`. `SERVER_PORT` también puede sobrescribirlo mediante la configuración nativa de Spring. |
| `APP_JOBS_STORAGE_CLEANUP_CRON` | Cada cinco minutos (`0 */5 * * * *`); `-` desactiva la limpieza de objetos pendientes. |
| `APP_CHECKIN_FRONTEND_ORIGIN` | Origen público del frontend para los QR de UC05; vacío por defecto. Requiere HTTPS, salvo HTTP en perfiles `dev` o `test`; sin un origen válido, el endpoint QR responde `503 QR_NOT_CONFIGURED`. |
| `MAIL_HOST`, `MAIL_PORT` | SMTP; `localhost`, `1025` (Mailpit). |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Credenciales SMTP opcionales. |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | `false` en local; configurar según el proveedor SMTP. |
| `APP_MAIL_FROM` | Remitente autorizado por el proveedor SMTP; dirección simple validada al arrancar. Opcional para Mailpit. |
| `APP_MAIL_CONTACT_URL` | URL del formulario de contacto del frontend; pendiente de definir para despliegue. Si está vacía, el código de acceso no se envía y la solicitud responde `503 MAIL_DELIVERY_FAILED`; tampoco se envían comprobantes ni avisos. |
| `APP_MAIL_CONTACT_RECIPIENT` | Destinatario de UC12: configurar `salcendra@unimagdalena.edu.co` mediante el entorno. Sin valor predeterminado en la aplicación; si está vacío, `POST /contact` responde `503 CONTACT_NOT_CONFIGURED`. No asigna roles de administrador. |
| `APP_MAIL_CANCELLATION_URL` | URL del flujo de cancelación del frontend con `{id}` como identificador de la reserva; pendiente de definir para despliegue. Sin ella no se envía el recordatorio. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión externa si no se usa la detección automática de Compose. |
| `APP_RULES_BOOKING_TOLERANCE_MINUTES`, `APP_RULES_LATE_CANCELLATION_MINUTES`, `APP_RULES_MIN_ADVANCE_MINUTES`, `APP_RULES_CHECKIN_OPENING_MINUTES` | Enteros no negativos; `10`, `60`, `0`, `10`. La aplicación rechaza configuraciones negativas al arrancar. |
| `APP_RULES_SUSPENSION_DAYS` | Entero positivo; `7`. |
| `APP_JOBS_RESERVATION_LIFECYCLE_CRON`, `APP_JOBS_REMINDER_CRON` | Cron de seis campos, cada minuto por defecto (`0 * * * * *`); `-` desactiva cada job. |
| `APP_JOBS_AUTH_CLEANUP_CRON` | Cada hora (`0 0 * * * *`); `-` lo desactiva. Limpia códigos vencidos/usados y solicitudes IP fuera de su retención. |

Con las claves ya configuradas en el entorno y Docker disponible, ejecute `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`). Mailpit expone su buzón local en `http://localhost:8025`. Para ejecutar el JAR fuera de la integración automática de Compose, configure también las variables de conexión a PostgreSQL y el servidor SMTP.

`./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`) ejecuta las pruebas con PostgreSQL y Mailpit mediante Testcontainers; las claves ficticias de pruebas no requieren configurar secretos reales. Incluye contratos, límites, concurrencia, rollback, expiración, configuración y seguridad. El resultado y número de pruebas de cada ejecución quedan en `target/surefire-reports/`; no se deduce el estado vigente de cifras de ejecuciones anteriores.

Para las pruebas no es necesario crear ni importar `.env`: Testcontainers y las configuraciones de prueba proporcionan sus propios valores. Ejecútelas en una consola sin `SPRING_CONFIG_IMPORT` ni variables de producción heredadas; por ejemplo, en PowerShell use `Remove-Item Env:SPRING_CONFIG_IMPORT -ErrorAction SilentlyContinue` antes de `.\mvnw.cmd clean verify`. Docker debe ser accesible para Testcontainers.

UC12 incluye pruebas de contrato, configuración y entrega desde `POST /contact` hasta SMTP con Mailpit, sin enviar a la cuenta institucional. Para el despliegue falta confirmar la URL pública del formulario React y establecer `APP_MAIL_CONTACT_URL`; las direcciones `localhost:5173` de la plantilla son solo locales. La navegación desde los correos, el comportamiento móvil, la accesibilidad y la recepción institucional requieren aceptación con el cliente y el proveedor reales.

La CSP configurada en la API restringe sus propias respuestas. La aplicación React se distribuye desde otro origen y su servidor debe proporcionar una CSP apropiada por separado.

### Preparación funcional para el cliente

La [guía de conexión del backend](docs/techincal-plan/backend-connection.md) contiene configuración local, arranque con Maven/Compose, comprobaciones HTTP sin frontend y requisitos para desplegar. Preparar la API no implica haber integrado el repositorio React.

El calendario y la creación usan la misma política temporal, incluida la anticipación mínima. ADMIN conserva consultas, cuota, contacto y operaciones sobre reservas propias; solo USER crea reservas. Los contratos están en `docs/techincal-plan/contracts/`.

El primer check-in respeta la ventana y rechaza una reserva de otro usuario en curso. Repetir sobre una reserva aún `IN_USE` devuelve su registro original, incluso después del fin antes de que el job la complete. Una suspensión cancela también reservas futuras con check-in anticipado completamente contenidas; no cancela las ya iniciadas.

Los indicadores de suspendidos se basan en auditorías `USER_SUSPENDED` y su fecha de fin conservada en `detail`. Para auditorías anteriores sin esa fecha se usa la última marca mensual creada no después del evento, si existe. No hace falta cambiar el esquema ni reparar Flyway para estos cambios.

Los envíos SMTP reintentan hasta dos veces los fallos de envío, con esperas de 100 y 200 ms; preparación y autenticación no se reintentan. Siguen siendo de mejor esfuerzo: no se garantizan recuperación tras caída ni ausencia de duplicados. La limpieza de autenticación conserva como mínimo una hora de solicitudes y, para códigos, el intervalo de reenvío si es mayor; no borra códigos activos ni reinicia los límites.

Para una prueba local con el cliente en `http://localhost:5173`, configure explícitamente `SPRING_PROFILES_ACTIVE=dev`, el origen QR y CORS con ese origen, contacto en `http://localhost:5173/app/contacto` y cancelación en `http://localhost:5173/reservations/{id}/cancel`. Sustituya el puerto si el cliente usa otro. Esto solo prepara configuración: no realiza la integración ni prueba navegación, dispositivos o accesibilidad del frontend.

En producción se requiere HTTPS y HSTS en el punto de entrada. Por defecto no se confía en encabezados reenviados: el límite por IP usa la dirección remota de la conexión. Si existe un proxy, configure explícitamente su confianza y evite que la API sea accesible directamente; no habilite la confianza indiscriminada en `X-Forwarded-For`. No active logs de cuerpos HTTP, credenciales SMTP o tokens.

La migración única `src/main/resources/db/migration/V1__shared_schema.sql` es editable durante el desarrollo. Cambiarla después de aplicarla provoca un conflicto de checksum en Flyway: coordine la recreación de las bases locales o de prueba, sin borrar datos que deban conservarse. El último cambio de este tipo añadió el índice `audit_log_action_created_idx` para los indicadores de UC09; las bases creadas antes deben recrearse.

### Versionado de reglas (UC06)

El diseño D-26 conserva versiones inmutables de cuota y umbral en `usage_parameters`; `usage_parameters_head` contiene únicamente la referencia vigente. Cada cambio efectivo crea una instantánea con número, UUID, fecha y administrador, y una auditoría `PARAMETERS_UPDATED` vinculada a esa versión. Las solicitudes sin cambios no crean otra versión. Los tres contratos REST de UC06 mantienen su forma; el historial se consulta en base de datos, sin endpoint adicional.

Esta extensión reescribe la V1 existente: no hay V2 ni actualización automática de bases creadas con el esquema anterior. La instalación nueva inicia en versión 1 con cuota 4, umbral 3 y autor nulo. La fecha inicial indica cuándo se instaló el esquema. Antes de recrear una base anterior, respalde y coordine la conservación de los datos necesarios; no basta con reparar el checksum, porque también cambia la estructura.

### Imágenes de espacios: Supabase Storage (UC07)

El backend recibe los archivos multipart, detecta su formato con ImageIO y los sube a un bucket privado mediante la API HTTP de Supabase. PostgreSQL conserva solamente `id`, `storage_key`, `content_type` y `size_bytes`; no guarda binarios ni enlaces temporales. Se conservan ambas imágenes obligatorias, el límite de 30 MiB por archivo y 64 MiB por solicitud. Se validan dimensiones antes de decodificar y se usa una muestra de hasta 1024 × 1024 píxeles para comprobar la decodificación sin crear el raster completo. Esa muestra se descarta: el archivo almacenado y entregado es el original, sin recodificar. La consulta `GET /spaces/{id}/images/{kind}` mantiene la autenticación Bearer y transmite el archivo mediante el service, sin revelar credenciales ni URL pública al cliente.

1. En el proyecto `https://teiezrgnhzarhhsycwsh.supabase.co`, mantener privado el bucket `space-images`, ya creado según el responsable del prototipo. Su límite de archivo debe admitir al menos 31.457.280 bytes; sus restricciones MIME deben admitir los formatos que se vayan a usar.
2. En la configuración API de Supabase, obtener una clave de servidor secreta o la heredada `service_role`. Configurar `APP_STORAGE_SUPABASE_URL`, `APP_STORAGE_SUPABASE_BUCKET` y `APP_STORAGE_SUPABASE_KEY` en el entorno del backend. `.env.example` incluye el origen y bucket indicados, pero deja la clave vacía. La plantilla requiere importación explícita como se explica arriba; Compose recibe las variables en el servicio `app`.
3. Subir las imágenes desde el cliente a los endpoints existentes; el backend hace el resto. No hace falta conectar el frontend con Supabase ni sustituir la autenticación de la aplicación. La credencial de servidor tiene privilegios elevados: nunca usar `anon`/`publishable` para esta integración ni publicar la clave secreta.

Las subidas ocurren antes de la transacción de negocio, sin mantener su bloqueo global durante la transferencia. Antes de cada subida se registra una compensación persistente en `storage_object_cleanup`, con una hora de margen. El plazo total de subida es inferior a ese margen. Al terminar, también si falla, se renueva la compensación por otra hora y se recrea si una limpieza excepcionalmente adelantada ya la retiró. Al confirmar las referencias, se retiran esas compensaciones en la misma transacción; si una referencia expiró, no se confirma el espacio parcialmente. Si la subida o la transacción falla, el job elimina los objetos sin referencia después del margen, incluso tras reiniciar el backend. En reemplazos se encola el objeto anterior en la transacción y se elimina después del commit; los fallos de Storage se reintentan a los cinco minutos. Cada ejecución procesa hasta 20 entradas y comprueba que ningún objeto a borrar esté referenciado. No se reintentan automáticamente las subidas.

La limpieza usa un scheduler dedicado, separado de los tres hilos de los jobs de negocio. Mantiene una conexión y el bloqueo de la fila de compensación durante cada borrado, pero no el bloqueo global de reservas. El plazo total también acota el borrado; la conexión compartida a PostgreSQL sigue siendo un recurso que debe dimensionarse.

Sin configuración de Storage, el resto del backend puede iniciar; las operaciones que lo necesitan responden `503 STORAGE_NOT_CONFIGURED`. Los errores remotos, permisos incorrectos o archivos remotos ausentes responden `503 STORAGE_UNAVAILABLE`, sin exponer la respuesta del proveedor. Un corte después de empezar a transmitir puede truncar la descarga: ya no es posible reemplazarla por un error JSON. Si el alojamiento suspende el backend, también se aplaza la limpieza. Las descargas consumen transferencia tanto de Supabase como del backend; los planes gratuitos no son ilimitados.

Este cambio también reescribe la V1 editable y añade la cola de limpieza: una base creada anteriormente no se actualiza automáticamente. El responsable confirmó que no hay imágenes ni datos que migrar; no se incluye una migración de binarios ni se recrean bases automáticamente. Mantener estables el proyecto y bucket mientras haya referencias o compensaciones pendientes.

Las pruebas del cliente Storage usan un servidor HTTP local y las pruebas de integración usan un doble de almacenamiento con PostgreSQL de Testcontainers; no utilizan credenciales ni archivos reales de Supabase. La aceptación contra el bucket real queda pendiente de configurar la clave en el servidor.

### Salud, integración y aceptación de despliegue

Actuator publica únicamente `GET /health`, `/health/liveness` y `/health/readiness`, sin autenticación ni detalles internos. Liveness representa el proceso; readiness comprueba aceptación de tráfico y PostgreSQL. Un fallo de readiness responde 503 sin implicar que deba reiniciarse el proceso. SMTP y Supabase no se consultan desde estas sondas. El `HEALTHCHECK` de la imagen y Compose usan `/health/readiness` mediante `docker/healthcheck.sh`; no requieren instalar curl y respetan `SERVER_PORT`, `PORT` o 8080.

Hikari espera como máximo 2 segundos para obtener una conexión y 1 segundo para validarla; no son un timeout general de todas las consultas SQL. El cierre por fase de Spring se limita a 5 segundos y Compose concede 30 segundos para detener el proceso. Las solicitudes interrumpidas no equivalen a operaciones confirmadas: el cliente debe comprobar el resultado antes de repetir escrituras.

Para mostrar imágenes privadas, React debe descargarlas con `fetch` y `Authorization: Bearer`, convertir la respuesta a blob y gestionar la URL de objeto (incluida su liberación). Un `<img src>` convencional no añade ese encabezado. Los errores tempranos de descarga conservan CORS y cabeceras de seguridad; después de iniciar la entrega, un corte solo puede dar una descarga incompleta.

Antes de aceptar un despliegue:

- Configurar PostgreSQL de destino (JDBC, TLS, permisos de Flyway y `btree_gist`) y comprobar una instalación limpia. Si se usa un pooler externo, verificar su compatibilidad; contratar Storage no configura la base de datos del backend.
- Configurar Supabase privado y probar subida, lectura, reemplazo y limpieza con la clave de servidor real. Verificar límites de archivo, MIME, capacidad y transferencia.
- Configurar SMTP, remitente y enlaces reales; comprobar recepción en los destinatarios previstos. Mailpit solo prueba entrega local. Revisar restricciones de puertos del hosting.
- Fijar CORS, orígenes QR, URLs de contacto y cancelación, HTTPS/HSTS y confianza del proxy. Probar puerto, límites multipart y tiempos de espera. No activar `dev` en producción.
- Para SSE, desactivar buffering del proxy, configurar su timeout y reconexión del cliente. Actualmente se envía un comentario inicial, no un heartbeat periódico; aceptar ese límite o acordar su implementación antes de exigir conexiones inactivas persistentes.
- Si el hosting se duerme, aceptar el retraso de los jobs funcionales y de limpieza; no equivale a operación continua.
- Completar aceptación con React: perfil, expiración y retorno al destino, reautenticación, multipart, imágenes, SSE, reservas, QR y enlaces. Las pruebas locales no sustituyen la aceptación del frontend y de los servicios reales.
