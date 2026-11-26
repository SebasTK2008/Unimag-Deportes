# Unimag-Deportes
Plataforma de reservas de espacios deportivos en la universidad del magdalena

## Desarrollo local

El backend usa Spring Boot 4.2.0 y requiere Java 25, PostgreSQL y dos claves externas independientes de al menos 32 bytes: `APP_AUTH_JWT_SECRET` y `APP_AUTH_CODE_HMAC_SECRET`. No guarde claves reales en el repositorio. Brevo es el SMTP predeterminado; Mailpit es una alternativa local optativa. `compose.yaml` inicia PostgreSQL y el proyecto incluye integración de Docker Compose para desarrollo.

El flujo de UC01 usa `POST /auth/code` y `POST /auth/verify`; luego se completa el nombre mediante `PUT /me/profile` si `needsName` es `true`. Consulte los [contratos de autenticación](docs/techincal-plan/contracts/README.md#autenticación-uc01). El token dura 24 horas y se envía como `Authorization: Bearer`, nunca en cookies. El cliente React, en otro repositorio, conserva el destino de navegación y elimina su token al cerrar sesión.

Con el perfil opcional `dev`, `POST /dev/login` acepta JSON con `email` institucional y `name`, crea el usuario si hace falta y devuelve un JWT en `token`. Este acceso no verifica el control del correo y no existe fuera del perfil `dev`; no debe habilitarse en producción. `APP_AUTH_ADMIN_EMAILS` determina únicamente el rol inicial de los usuarios nuevos.

### Configuración

El repositorio incluye `.env.example` como plantilla local para un cliente en `http://localhost:5173`; `.env` y sus variantes están excluidos de Git. La plantilla activa `SPRING_PROFILES_ACTIVE=dev` y contiene marcadores `SECRET-KEY` para SMTP y Storage: no son credenciales válidas. Revise esos valores antes de arrancar; para deshabilitar Storage, deje su clave vacía. El archivo no se carga automáticamente en Spring. Para usarlo, cópielo si no existe, complete las claves obligatorias y configure explícitamente la importación desde la raíz del proyecto:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Edite .env: establezca claves independientes y los valores necesarios.
$env:SPRING_CONFIG_IMPORT = 'file:.env[.properties]'
.\mvnw.cmd spring-boot:run
```

En macOS/Linux, use `cp .env.example .env` y `SPRING_CONFIG_IMPORT='file:.env[.properties]' ./mvnw spring-boot:run`. Las variables del entorno tienen prioridad sobre el archivo importado. No incluya el prefijo `export` ni comillas de shell en `.env`: se interpreta como un archivo de propiedades de Spring. El perfil `dev` permite HTTP para el frontend local y habilita el login sin OTP; elimínelo de los perfiles activos al preparar un despliegue.

Para desplegar el JAR, inyecte las variables directamente mediante el entorno o el gestor de secretos de la plataforma; `.env.example` no es una configuración de producción. Configure PostgreSQL y correo externos, los orígenes públicos y credenciales apropiados, y no incluya `.env` ni secretos en la imagen o repositorio. En Render seleccione `APP_MAIL_PROVIDER=brevo` para enviar por HTTPS, como se explica en la sección de correo. Por defecto, `compose.yaml` inicia solo PostgreSQL; el perfil `app` incluye el backend con Brevo por SMTP y el perfil `mailpit` permite iniciar el capturador local. Este Compose conserva credenciales y servicios de desarrollo: no es una configuración de producción.

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
| `APP_MAIL_PROVIDER` | `smtp` por compatibilidad local; `brevo` selecciona la API HTTPS. Un valor distinto impide el arranque; no hay fallback entre transportes. |
| `APP_MAIL_BREVO_API_KEY` | API key de Brevo, obligatoria al seleccionar `brevo`. No es la clave SMTP; nunca exponer al frontend. |
| `APP_MAIL_BREVO_CONNECT_TIMEOUT`, `APP_MAIL_BREVO_REQUEST_TIMEOUT` | Duraciones ISO-8601; `PT5S` y `PT10S`. Cada una debe estar entre 1 ms y un minuto. Solo se validan en modo `brevo`. |
| `MAIL_HOST`, `MAIL_PORT` | SMTP; `smtp-relay.brevo.com`, `587`, tanto en Spring como en Compose. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Vacías por defecto; configure las credenciales SMTP de Brevo. Para Mailpit deben quedar vacías. |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | `true` por defecto para Brevo; `false` con Mailpit local. |
| `MAIL_SMTP_STARTTLS_REQUIRED` | `true` por defecto: rechaza conexiones sin STARTTLS. Use `false` con Mailpit local. |
| `APP_MAIL_FROM` | Remitente autorizado por Brevo; dirección simple validada al arrancar. Obligatoria en modo HTTPS, opcional para SMTP/Mailpit. |
| `APP_MAIL_FROM_NAME` | Nombre visible del remitente, separado de la dirección; vacío por defecto. La plantilla usa `Unimag Deportes`. No admite saltos de línea. Sin dirección no se establece el encabezado From. |
| `APP_MAIL_CONTACT_URL` | URL del formulario de contacto del frontend; pendiente de definir para despliegue. Si está vacía, el código de acceso no se envía y la solicitud responde `503 MAIL_DELIVERY_FAILED`; tampoco se envían comprobantes ni avisos. |
| `APP_MAIL_CONTACT_RECIPIENT` | Destinatario de UC12: configurar `salcendra@unimagdalena.edu.co` mediante el entorno. Sin valor predeterminado en la aplicación; si está vacío, `POST /contact` responde `503 CONTACT_NOT_CONFIGURED`. No asigna roles de administrador. |
| `APP_MAIL_CANCELLATION_URL` | URL del flujo de cancelación del frontend con `{id}` como identificador de la reserva; pendiente de definir para despliegue. Sin ella no se envía el recordatorio. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión externa si no se usa la detección automática de Compose. |
| `APP_RULES_BOOKING_TOLERANCE_MINUTES`, `APP_RULES_LATE_CANCELLATION_MINUTES`, `APP_RULES_MIN_ADVANCE_MINUTES`, `APP_RULES_CHECKIN_OPENING_MINUTES` | Enteros no negativos; `10`, `60`, `0`, `10`. La aplicación rechaza configuraciones negativas al arrancar. |
| `APP_RULES_SUSPENSION_DAYS` | Entero positivo; `7`. |
| `APP_JOBS_RESERVATION_LIFECYCLE_CRON`, `APP_JOBS_REMINDER_CRON` | Cron de seis campos, cada minuto por defecto (`0 * * * * *`); `-` desactiva cada job. |
| `APP_JOBS_AUTH_CLEANUP_CRON` | Cada hora (`0 0 * * * *`); `-` lo desactiva. Limpia códigos vencidos/usados y solicitudes IP fuera de su retención. |

Con las claves y credenciales SMTP ya configuradas en el entorno y Docker disponible, ejecute `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`). La integración automática detecta PostgreSQL, pero no sustituye Brevo por Mailpit: ese servicio está excluido de la autodetección de Spring. Para ejecutar el JAR fuera de esa integración, configure también las variables de conexión a PostgreSQL.

### Arranque con Docker

Con `.env` privado completado, el backend y PostgreSQL se levantan con Brevo, sin Mailpit:

```powershell
docker compose --profile app up --build -d
```

Compose lee `.env` para interpolar las variables enumeradas en `compose.yaml`; no necesita `SPRING_CONFIG_IMPORT`. La API se publica en `http://localhost:8080` por defecto. Cambie `APP_PORT` para modificar el puerto del host; el contenedor escucha en `8080`.

El `Dockerfile` usa una etapa de construcción con Maven y Java 25 y una imagen final JRE 25. Declara el usuario no privilegiado `app` con UID/GID `10001:10001`; el JAR y el script de salud permanecen propiedad de root y no requieren permisos de escritura del proceso. Las cargas multipart usan el directorio temporal del sistema. La construcción omite la ejecución de pruebas: ejecute `clean verify` por separado antes de aceptar la imagen.

### Mailpit opcional

Para ejecutar también el backend en Docker con captura local, use el override:

```powershell
docker compose -f compose.yaml -f compose.mailpit.yaml --profile app up --build -d
```

Este modo inicia Mailpit, dirige SMTP a `mailpit:1025`, vacía usuario y contraseña y desactiva autenticación y STARTTLS, incluso si `.env` contiene valores de Brevo. Requiere una versión de Docker Compose que admita `!reset`. El buzón local está en `http://localhost:8025`, salvo que cambie `MAILPIT_WEB_PORT`.

Para usar Mailpit con Maven/JAR en el host, levante los servicios locales y sobrescriba SMTP explícitamente antes de ejecutar Spring:

```powershell
docker compose --profile mailpit up -d
$env:APP_MAIL_PROVIDER = 'smtp'
$env:MAIL_HOST = 'localhost'
$env:MAIL_PORT = '1025' # Use MAILPIT_SMTP_PORT si cambió el puerto publicado.
$env:MAIL_USERNAME = ''
$env:MAIL_PASSWORD = ''
$env:MAIL_SMTP_AUTH = 'false'
$env:MAIL_SMTP_STARTTLS = 'false'
$env:MAIL_SMTP_STARTTLS_REQUIRED = 'false'
$env:SPRING_CONFIG_IMPORT = 'file:.env[.properties]'
.\mvnw.cmd spring-boot:run
```

Algunas consolas eliminan una variable de entorno al asignarle una cadena vacía. Si su `.env` contiene credenciales Brevo, deje también `MAIL_USERNAME` y `MAIL_PASSWORD` vacíos en ese archivo para el modo local. No ejecute a la vez el backend de Maven y el de Compose en el mismo puerto. Para regresar a Brevo, retire las sobrescrituras locales y restaure sus credenciales; al volver a Compose, use solo `compose.yaml`. No use `docker compose down -v` para cambiar de modo: borra los datos de PostgreSQL.

### Pruebas automatizadas

`./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`) ejecuta las pruebas con PostgreSQL y Mailpit mediante Testcontainers; las claves ficticias de pruebas no requieren configurar secretos reales. Incluye contratos, límites, concurrencia, rollback, expiración, configuración y seguridad. El resultado y número de pruebas de cada ejecución quedan en `target/surefire-reports/`; no se deduce el estado vigente de cifras de ejecuciones anteriores.

Para las pruebas no es necesario crear ni importar `.env`: Testcontainers y las configuraciones de prueba proporcionan sus propios valores. Ejecútelas en una consola sin `SPRING_CONFIG_IMPORT` ni variables de producción heredadas; por ejemplo, en PowerShell use `Remove-Item Env:SPRING_CONFIG_IMPORT -ErrorAction SilentlyContinue` antes de `.\mvnw.cmd clean verify`. Docker debe ser accesible para Testcontainers.

La verificación del 24 de noviembre de 2026, posterior al cambio a Brevo predeterminado y Mailpit optativo, terminó con `BUILD SUCCESS`: **347 pruebas, cero fallos, errores u omisiones**, y generó `target/demo-0.0.1-SNAPSHOT.jar`. Incluye los defaults SMTP y la selección explícita de Mailpit. Las integraciones continúan usando servicios aislados y credenciales ficticias; no acreditan entrega institucional ni acceso al bucket real.

También se construyó y ejecutó la imagen con UID efectivo `10001`, PostgreSQL nuevo y Mailpit aislados, sin perfil `dev`. Se comprobaron login OTP, perfil, roles, CORS, sondas y script de salud. Una carga multipart de 4 MiB con imágenes deliberadamente inválidas llegó a la validación del dominio y respondió `400 VALIDATION_ERROR`, sin errores de permisos; no fue una subida a Supabase. El directorio temporal resultó escribible y el JAR no escribible por el proceso. Al detener PostgreSQL, readiness respondió 503 y liveness 200; el servicio recuperó readiness al reiniciar la base. El apagado fue ordenado, sin terminación forzada. La configuración Compose base y el override Mailpit se validaron mediante `docker compose config`; estos resultados no equivalen a aceptar servicios externos ni el frontend.

UC12 incluye pruebas de contrato, configuración y entrega desde `POST /contact` hasta SMTP con Mailpit, sin enviar a la cuenta institucional. Para el despliegue falta confirmar la URL pública del formulario React y establecer `APP_MAIL_CONTACT_URL`; las direcciones `localhost:5173` de la plantilla son solo locales. La navegación desde los correos, el comportamiento móvil, la accesibilidad y la recepción institucional requieren aceptación con el cliente y el proveedor reales.

La CSP configurada en la API restringe sus propias respuestas. La aplicación React se distribuye desde otro origen y su servidor debe proporcionar una CSP apropiada por separado.

### Correo transaccional con Brevo

El comprobante usa la plantilla definitiva incluida en `src/main/resources/pdf/comprobante-definitivo.pdf`, con logos, firma gráfica y diseño conservados. Solo se rellenan los datos y se unifica el área de implementos en una lista sin tres filas fijas; los artículos que no caben continúan en otra copia de la misma plantilla. Las reglas de cancelación y la orientación de check-in se envían en el cuerpo del correo, no en el PDF. Confirmaciones, avisos y códigos incluyen HTML y texto plano: el código aparece centrado y grande; las cancelaciones explican la causa conocida y sus efectos sobre horas, espacio e implementos.

#### HTTPS para Render

Seleccione explícitamente el proveedor y configure una **API key**, distinta de las credenciales SMTP:

```properties
APP_MAIL_PROVIDER=brevo
APP_MAIL_BREVO_API_KEY=<API key de Brevo>
APP_MAIL_BREVO_CONNECT_TIMEOUT=PT5S
APP_MAIL_BREVO_REQUEST_TIMEOUT=PT10S
APP_MAIL_FROM=unimagdeportes@gmail.com
APP_MAIL_FROM_NAME=Unimag Deportes
```

`BrevoHttpMailSender` envía JSON a `https://api.brevo.com/v3/smtp/email` por HTTPS/443 con el encabezado `api-key`. El nombre de la ruta no implica una conexión SMTP. Envía las versiones HTML/texto y el PDF original codificado en Base64; los mensajes de texto tienen también una alternativa HTML escapada. No requiere `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` ni `MAIL_PASSWORD`. Mantenga configuradas las URLs reales de contacto y cancelación y el destinatario de contacto.

El modo HTTPS exige clave y remitente válidos al arrancar; SMTP no es un fallback. Solo HTTP 201 indica aceptación del envío inmediato por Brevo, no recepción en el buzón. No se siguen redirecciones ni se reintenta automáticamente, incluidos 429, 5xx y timeouts: el proveedor podría haber aceptado un envío antes de un fallo de conexión y repetirlo podría duplicarlo. Los errores conservan `503 MAIL_DELIVERY_FAILED` en acceso/contacto; el fallo de comprobantes o notificaciones no deshace una operación de negocio confirmada. No hay cola persistente ni recuperación garantizada.

Para Render, inyecte las variables en Environment, use el `Dockerfile` existente y no active `dev`. El proveedor SMTP sigue siendo el predeterminado para no romper entornos existentes. Para Mailpit con Maven seleccione `APP_MAIL_PROVIDER=smtp`; el override `compose.mailpit.yaml` lo impone explícitamente. Cambiar el transporte no evita la suspensión de Render Free: mientras el backend duerme no se ejecutan los jobs. Las sondas de salud no consultan Brevo.

Las pruebas HTTPS usan un servidor HTTP local y credenciales ficticias; la recepción real desde Render, el remitente autorizado y los límites de la cuenta Brevo deben comprobarse por separado.

La imagen se construyó y arrancó con PostgreSQL aislado, `APP_MAIL_PROVIDER=brevo`, clave API ficticia y sin variables SMTP ni perfil `dev`. Alcanzó readiness saludable con UID `10001`; no se enviaron correos a Brevo. Esta comprobación acredita arranque y configuración, no entrega externa.

La verificación posterior al adaptador HTTPS, del 24 de noviembre de 2026, terminó con `.\mvnw.cmd clean verify`: **376 pruebas, cero fallos, errores u omisiones**. Incluye contrato HTTP simulado, selección de transporte, timeout, login, invalidación de códigos ante fallos, comprobante PDF, contacto y cancelación; también conserva la regresión SMTP/Mailpit. Se generó `target/demo-0.0.1-SNAPSHOT.jar`.

#### SMTP y Mailpit local

El adaptador SMTP existente admite Brevo sin SDK ni cambios en los endpoints. Configure el remitente en Brevo con nombre `Unimag Deportes` y dirección `unimagdeportes@gmail.com`, y complete su verificación. No se puede autenticar `gmail.com` mediante DNS propios; Brevo puede adaptar la dirección remitente y la recepción institucional debe comprobarse con envíos reales. Verificar el buzón no garantiza entrega ni ausencia de spam.

Inyecte los siguientes valores en el entorno del backend o en un `.env` privado importado explícitamente:

```properties
APP_MAIL_PROVIDER=smtp
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=<usuario SMTP de Brevo>
MAIL_PASSWORD=<clave SMTP de Brevo>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MAIL_SMTP_STARTTLS_REQUIRED=true
APP_MAIL_FROM=unimagdeportes@gmail.com
APP_MAIL_FROM_NAME=Unimag Deportes
```

Use una clave SMTP, no una API key ni la contraseña de la cuenta. No guarde credenciales reales en archivos versionados ni active logs SMTP de depuración. Mantenga configuradas las URLs de contacto y cancelación indicadas en la tabla.

Con Maven, puede conservar la integración automática de PostgreSQL: Mailpit no sustituye al SMTP externo, aunque su contenedor esté iniciado. Para ejecutar el JAR con PostgreSQL externo, establezca `SPRING_DOCKER_COMPOSE_ENABLED=false` y configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. En Spring y en el servicio `app` de Compose, omitir `MAIL_HOST` y `MAIL_PORT` selecciona Brevo en el puerto 587. Una configuración anterior con `MAIL_HOST=localhost` debe retirarse al volver a Brevo; para Mailpit use las opciones explícitas descritas arriba.

Las pruebas automáticas continúan usando Mailpit y credenciales ficticias. La aceptación real requiere comprobar código de acceso, comprobante PDF, notificaciones, contacto, enlaces y nombre visible en el buzón institucional y en los registros transaccionales de Brevo. Los detalles están en la [guía de conexión](docs/techincal-plan/backend-connection.md#8-validar-correo-con-brevo).

### Preparación funcional para el cliente

**Cancelación de bloqueos implementada (D-27):** cualquier ADMIN reautenticado puede cancelar una fila futura o en curso con motivo, historial y auditoría, sin reactivar reservas ni modificar cuota/implementos. No levanta mantenimiento ni otros bloqueos. El [listado administrativo](docs/techincal-plan/contracts/blocks/GET-admin-blocks.md) obtiene IDs e historial; la [cancelación individual](docs/techincal-plan/contracts/blocks/POST-admin-blocks-id-cancellation.md) es idempotente y no envía correos de levantamiento. React debe integrar confirmación, reautenticación e invalidación REST/SSE; esa integración no se realizó en este repositorio.

**Ampliación aprobada D-28:** añadir nombres actuales de creador/cancelador al listado y cancelación (`createdBy`, `createdByName`, `cancelledByName`), conservando IDs internos. El creador se resuelve desde auditoría y ambos nombres desde perfiles, sin nueva migración ni cambios en espacios/reservas. Autoría ausente o contradictoria no se inventa. React debe mostrar nombres en lugar de UUID, con «Administrador no disponible» si falta nombre; la integración visual está pendiente. El plan UC07 registra implementación y pruebas de esta ampliación por separado de la evidencia histórica siguiente.

La verificación final del 25 de noviembre de 2026 ejecutó `.\mvnw.cmd clean verify` con **394 pruebas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. Incluye cancelación, historial, permisos, reintentos, no reactivación, SSE, una carrera con espera observada en PostgreSQL y migración V1→V2 con datos conservados. La observación de `@auditor` sobre la prueba de carrera fue corregida antes de esa ejecución. Véase la evidencia y los antecedentes en el [plan UC07](docs/features/007-gestionar-espacios-y-franjas/plan.md#ampliación-aprobada-2026-11-25-cancelar-bloqueos).

La [guía de conexión del backend](docs/techincal-plan/backend-connection.md) contiene comprobaciones HTTP sin frontend y requisitos para desplegar. Sus instrucciones de selección SMTP anteriores al cambio de defaults deben contrastarse con las secciones de arranque de este README: Mailpit ya no se inicia ni se selecciona automáticamente. Preparar la API no implica haber integrado el repositorio React.

El calendario y la creación usan la misma política temporal, incluida la anticipación mínima. ADMIN conserva consultas, cuota, contacto y operaciones sobre reservas propias; solo USER crea reservas. Los contratos están en `docs/techincal-plan/contracts/`.

Cada usuario puede reservar como máximo 2 horas acumuladas por día en America/Bogota, entre todos los espacios, además de su cuota semanal. Ambas cuotas cuentan los mismos estados. Se permiten dos reservas consecutivas de una hora, sin fusionarlas ni modificar datos históricos.

El primer check-in admite hasta una hora después del fin de la reserva, inclusive; solo después puede declararse inasistencia. `GET /checkin/{spaceId}/reservations` lista las reservas propias pendientes dentro de su ventana y `POST /checkin/{spaceId}/reservations/{reservationId}` confirma la elegida. La asistencia tardía deja la reserva `COMPLETED` y se permite aunque otro usuario ocupe el espacio; no extiende su uso. Repetir por identificador sobre `IN_USE` o `COMPLETED` con asistencia conserva la fecha y no duplica auditoría. El POST anterior se conserva, pero exige selección si hay varias elegibles. Véanse los [contratos de check-in](docs/techincal-plan/contracts/README.md#check-in-y-qr-uc05).

Una suspensión cancela también reservas futuras con check-in anticipado completamente contenidas; no cancela las ya iniciadas.

Los indicadores de suspendidos se basan en auditorías `USER_SUSPENDED` y su fecha de fin conservada en `detail`. Para auditorías anteriores sin esa fecha se usa la última marca mensual creada no después del evento, si existe. No hace falta cambiar el esquema ni reparar Flyway para estos cambios.

Los envíos SMTP reintentan hasta dos veces los fallos de envío, con esperas de 100 y 200 ms; preparación y autenticación no se reintentan. El adaptador HTTPS de Brevo no reintenta. Ambos siguen siendo de mejor esfuerzo: no se garantizan recuperación tras caída ni ausencia de duplicados. La limpieza de autenticación conserva como mínimo una hora de solicitudes y, para códigos, el intervalo de reenvío si es mayor; no borra códigos activos ni reinicia los límites.

Para una prueba local con el cliente en `http://localhost:5173`, configure explícitamente `SPRING_PROFILES_ACTIVE=dev`, el origen QR y CORS con ese origen, contacto en `http://localhost:5173/app/contacto` y cancelación en `http://localhost:5173/reservations/{id}/cancel`. Sustituya el puerto si el cliente usa otro. Esto solo prepara configuración: no realiza la integración ni prueba navegación, dispositivos o accesibilidad del frontend.

En producción se requiere HTTPS y HSTS en el punto de entrada. Por defecto no se confía en encabezados reenviados: el límite por IP usa la dirección remota de la conexión. Si existe un proxy, configure explícitamente su confianza y evite que la API sea accesible directamente; no habilite la confianza indiscriminada en `X-Forwarded-For`. No active logs de cuerpos HTTP, credenciales SMTP o tokens.

Para esta ampliación se conserva `V1__shared_schema.sql` y se añade `V2__institutional_block_cancellation.sql`: Flyway conserva filas y añade estado `ACTIVE` y metadatos nulos a bloqueos existentes. Una instalación nueva aplica ambas migraciones. Una base con la V1 actual aplica V2 sin recreación; una V1 histórica distinta sigue necesitando respaldo y reconciliación previa. No borrar datos ni reparar solo el checksum para ocultar diferencias. Las notas fechadas de cambios anteriores que reescribieron V1 son históricas; no describen la estrategia de esta ampliación.

### Versionado de reglas (UC06)

El diseño D-26 conserva versiones inmutables de cuota y umbral en `usage_parameters`; `usage_parameters_head` contiene únicamente la referencia vigente. Cada cambio efectivo crea una instantánea con número, UUID, fecha y administrador, y una auditoría `PARAMETERS_UPDATED` vinculada a esa versión. Las solicitudes sin cambios no crean otra versión. Los tres contratos REST de UC06 mantienen su forma; el historial se consulta en base de datos, sin endpoint adicional.

**Antecedente anterior a D-27:** la extensión de parámetros reescribió V1, sin migración incremental para aquellas bases anteriores. La instalación inicia los parámetros en versión 1 con cuota 4, umbral 3 y autor nulo; la fecha inicial indica la instalación, no un cambio histórico recuperado. D-27 añade V2 para cancelación de bloqueos sin reescribir la V1 actual. Una V1 histórica distinta requiere respaldo y reconciliación, no solo reparar checksum.

### Imágenes de espacios: Supabase Storage (UC07)

El backend recibe los archivos multipart, detecta su formato con ImageIO y los sube a un bucket privado mediante la API HTTP de Supabase. PostgreSQL conserva solamente `id`, `storage_key`, `content_type` y `size_bytes`; no guarda binarios ni enlaces temporales. Se conservan ambas imágenes obligatorias, el límite de 30 MiB por archivo y 64 MiB por solicitud. Se validan dimensiones antes de decodificar y se usa una muestra de hasta 1024 × 1024 píxeles para comprobar la decodificación sin crear el raster completo. Esa muestra se descarta: el archivo almacenado y entregado es el original, sin recodificar. La consulta `GET /spaces/{id}/images/{kind}` mantiene la autenticación Bearer y transmite el archivo mediante el service, sin revelar credenciales ni URL pública al cliente.

1. En el proyecto `https://teiezrgnhzarhhsycwsh.supabase.co`, mantener privado el bucket `space-images`, ya creado según el responsable del prototipo. Su límite de archivo debe admitir al menos 31.457.280 bytes; sus restricciones MIME deben admitir los formatos que se vayan a usar.
2. En la configuración API de Supabase, obtener una clave de servidor secreta o la heredada `service_role`. Configurar `APP_STORAGE_SUPABASE_URL`, `APP_STORAGE_SUPABASE_BUCKET` y `APP_STORAGE_SUPABASE_KEY` en el entorno del backend. `.env.example` incluye el origen y bucket indicados y el marcador `SECRET-KEY`, que debe sustituirse por una clave válida o dejarse vacío para deshabilitar Storage; el marcador no equivale a configuración ausente. La plantilla requiere importación explícita como se explica arriba; Compose recibe las variables en el servicio `app`.
3. Subir las imágenes desde el cliente a los endpoints existentes; el backend hace el resto. No hace falta conectar el frontend con Supabase ni sustituir la autenticación de la aplicación. La credencial de servidor tiene privilegios elevados: nunca usar `anon`/`publishable` para esta integración ni publicar la clave secreta.

Las subidas ocurren antes de la transacción de negocio, sin mantener su bloqueo global durante la transferencia. Antes de cada subida se registra una compensación persistente en `storage_object_cleanup`, con una hora de margen. El plazo total de subida es inferior a ese margen. Al terminar, también si falla, se renueva la compensación por otra hora y se recrea si una limpieza excepcionalmente adelantada ya la retiró. Al confirmar las referencias, se retiran esas compensaciones en la misma transacción; si una referencia expiró, no se confirma el espacio parcialmente. Si la subida o la transacción falla, el job elimina los objetos sin referencia después del margen, incluso tras reiniciar el backend. En reemplazos se encola el objeto anterior en la transacción y se elimina después del commit; los fallos de Storage se reintentan a los cinco minutos. Cada ejecución procesa hasta 20 entradas y comprueba que ningún objeto a borrar esté referenciado. No se reintentan automáticamente las subidas.

La limpieza usa un scheduler dedicado, separado de los tres hilos de los jobs de negocio. Mantiene una conexión y el bloqueo de la fila de compensación durante cada borrado, pero no el bloqueo global de reservas. El plazo total también acota el borrado; la conexión compartida a PostgreSQL sigue siendo un recurso que debe dimensionarse.

Sin configuración de Storage, el resto del backend puede iniciar; las operaciones que lo necesitan responden `503 STORAGE_NOT_CONFIGURED`. Los errores remotos, permisos incorrectos o archivos remotos ausentes responden `503 STORAGE_UNAVAILABLE`, sin exponer la respuesta del proveedor. Un corte después de empezar a transmitir puede truncar la descarga: ya no es posible reemplazarla por un error JSON. Si el alojamiento suspende el backend, también se aplaza la limpieza. Las descargas consumen transferencia tanto de Supabase como del backend; los planes gratuitos no son ilimitados.

**Antecedente anterior a D-27:** el cambio de Storage reescribió V1 y añadió la cola de limpieza, sin actualización automática de bases anteriores. El responsable confirmó entonces que no había imágenes ni datos que migrar; no se incluyó migración de binarios ni recreación automática. La V2 posterior de cancelación de bloqueos no cambia Storage ni reconcilia aquellas V1 antiguas. Mantener estables el proyecto y bucket mientras haya referencias o compensaciones pendientes.

Las pruebas del cliente Storage usan un servidor HTTP local y las pruebas de integración usan un doble de almacenamiento con PostgreSQL de Testcontainers; no utilizan credenciales ni archivos reales de Supabase. La aceptación contra el bucket real queda pendiente de configurar la clave en el servidor.

### Salud, integración y aceptación de despliegue

Actuator publica únicamente `GET /health`, `/health/liveness` y `/health/readiness`, sin autenticación ni detalles internos. Liveness representa el proceso; readiness comprueba aceptación de tráfico y PostgreSQL. Un fallo de readiness responde 503 sin implicar que deba reiniciarse el proceso. SMTP y Supabase no se consultan desde estas sondas. El `HEALTHCHECK` de la imagen y Compose usan `/health/readiness` mediante `docker/healthcheck.sh`; no requieren instalar curl y respetan `SERVER_PORT`, `PORT` o 8080.

Hikari espera como máximo 2 segundos para obtener una conexión y 1 segundo para validarla; no son un timeout general de todas las consultas SQL. El cierre por fase de Spring se limita a 5 segundos y Compose concede 30 segundos para detener el proceso. Las solicitudes interrumpidas no equivalen a operaciones confirmadas: el cliente debe comprobar el resultado antes de repetir escrituras.

Para mostrar imágenes privadas, React debe descargarlas con `fetch` y `Authorization: Bearer`, convertir la respuesta a blob y gestionar la URL de objeto (incluida su liberación). Un `<img src>` convencional no añade ese encabezado. Los errores tempranos de descarga conservan CORS y cabeceras de seguridad; después de iniciar la entrega, un corte solo puede dar una descarga incompleta.

Antes de aceptar un despliegue:

- Empezar con una sola réplica. SSE mantiene las suscripciones y eventos en el proceso; varias réplicas requieren difusión entre instancias y coordinación adicional de jobs. Los bloqueos transaccionales de PostgreSQL no convierten el envío de recordatorios en una entrega única distribuida.
- Configurar PostgreSQL de destino (JDBC, TLS, permisos de Flyway y `btree_gist`) y comprobar una instalación limpia. Si se usa un pooler externo, verificar su compatibilidad; contratar Storage no configura la base de datos del backend.
- Configurar Supabase privado y probar subida, lectura, reemplazo y limpieza con la clave de servidor real. Verificar límites de archivo, MIME, capacidad y transferencia.
- Configurar correo, remitente y enlaces reales; comprobar recepción en los destinatarios previstos. Para Render usar Brevo HTTPS con API key. Mailpit solo prueba entrega local. Revisar restricciones de puertos si se selecciona SMTP.
- Fijar CORS, orígenes QR, URLs de contacto y cancelación, HTTPS/HSTS y confianza del proxy. Probar puerto, límites multipart y tiempos de espera. No activar `dev` en producción.
- Para SSE, desactivar buffering del proxy, configurar su timeout y reconexión del cliente. Actualmente se envía un comentario inicial, no un heartbeat periódico; aceptar ese límite o acordar su implementación antes de exigir conexiones inactivas persistentes.
- Si el hosting se duerme, aceptar el retraso de los jobs funcionales y de limpieza; no equivale a operación continua.
- Completar aceptación con React: perfil, expiración y retorno al destino, reautenticación, multipart, imágenes, SSE, reservas, QR y enlaces. Las pruebas locales no sustituyen la aceptación del frontend y de los servicios reales.
