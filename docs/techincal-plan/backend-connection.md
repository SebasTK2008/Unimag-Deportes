# Backend preparado para la conexión del frontend

Esta guía prepara y comprueba la API sin ejecutar ni modificar el cliente React. La integración visual y la navegación del cliente se validarán por separado.

El cliente de `../UnimagDeportes_Frontend` dispone de conexión HTTP mediante
`VITE_API_URL` y proxy local mediante `VITE_BACKEND_URL`. Su demostración es opcional:
`VITE_DEMO_LOGIN=false` la desactiva por defecto; solo `true` habilita las sesiones
simuladas del navegador, independientes de `/dev/login`. Consulte el README del
cliente para ejecución, pruebas y despliegue. Esto no acredita aceptación visual
ni entrega de correo institucional.

## Direcciones locales

| Servicio | Dirección predeterminada |
|---|---|
| API | `http://localhost:8080` |
| Frontend futuro | `http://localhost:5173` |
| PostgreSQL de Compose | `localhost:5433` |
| SMTP de Mailpit | `localhost:1025` |
| Buzón de Mailpit | `http://localhost:8025` |

La API no tiene prefijo `/api`. Por ejemplo, su ruta real es `/spaces`. Si el cliente expone `/api/spaces`, su proxy debe retirar `/api` al reenviar. El navegador puede usar el proxy o acceder directamente a la API con CORS.

## 1. Configurar el backend

Requisitos: Java 25 y Docker con soporte de contenedores Linux. Ejecute desde la raíz del backend:

```powershell
# Solo si todavía no existe; no sobrescriba su configuración.
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Edite `.env` antes de arrancar:

| Variable | Configuración local |
|---|---|
| `APP_AUTH_JWT_SECRET` | Clave aleatoria de al menos 32 bytes UTF-8. |
| `APP_AUTH_CODE_HMAC_SECRET` | Otra clave aleatoria independiente de al menos 32 bytes UTF-8. |
| `APP_AUTH_ADMIN_EMAILS` | Correo institucional de la cuenta administradora; varios correos se separan con comas. |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173`; varios orígenes se separan con comas, sin rutas ni barra final. |
| `APP_CHECKIN_FRONTEND_ORIGIN` | `http://localhost:5173` |
| `APP_MAIL_CONTACT_URL` | `http://localhost:5173/app/contacto` |
| `APP_MAIL_CANCELLATION_URL` | `http://localhost:5173/reservations/{id}/cancel` |
| `APP_MAIL_CONTACT_RECIPIENT` | `salcendra@unimagdalena.edu.co`; en local Mailpit captura el envío. |
| `SERVER_PORT` | `8080` para ejecución con Maven o JAR. |
| `APP_PORT` | `8080` para el puerto del host al ejecutar el servicio Compose `app`. |

Active explícitamente `SPRING_PROFILES_ACTIVE=dev` en `.env` para permitir HTTP en el origen QR local. El perfil también habilita `/dev/login`, que no verifica el control del correo; no lo use como prueba del flujo OTP ni lo active en producción.

La plantilla no incluye claves reales. No comparta `.env`, tokens ni credenciales, y no publique estas variables en el frontend. Spring interpreta el archivo como propiedades: sin `export` y sin comillas de shell. Las variables del proceso tienen prioridad.

`APP_AUTH_ADMIN_EMAILS` solo asigna el rol inicial a cuentas nuevas. Configure el correo antes de su primer acceso; cambiar esta variable no promueve cuentas existentes.

## 2. Arrancar

### Maven y servicios de Compose

```powershell
$env:SPRING_CONFIG_IMPORT = 'file:.env[.properties]'
.\mvnw.cmd spring-boot:run
```

Spring inicia los servicios de desarrollo de Compose. No configure `SPRING_DATASOURCE_*` si utiliza su detección automática. Si usa PostgreSQL externo, configure su conexión y gestione los servicios por separado.

### Backend también dentro de Docker

Como alternativa, en otra consola sin el proceso anterior:

```powershell
docker compose --profile app up --build -d
```

Compose lee `.env` para interpolar su configuración y pasa al contenedor las variables enumeradas en `compose.yaml`. El contenedor escucha internamente en `8080`; cambie `APP_PORT` para publicar otro puerto. `SERVER_PORT` de `.env` no se pasa a ese contenedor.

No ejecute ambas alternativas sobre los mismos puertos. No use `docker compose down -v` para resolver problemas: elimina datos. Si Flyway detecta un checksum distinto, coordine el respaldo y la conservación de la base; no repare ni recree automáticamente el esquema.

## 3. Comprobar CORS sin frontend

```powershell
$api = 'http://localhost:8080'
$origin = 'http://localhost:5173'

curl.exe -i -X OPTIONS "$api/me" `
  -H "Origin: $origin" `
  -H "Access-Control-Request-Method: GET" `
  -H "Access-Control-Request-Headers: authorization,content-type"
```

Se espera `200` y `Access-Control-Allow-Origin: http://localhost:5173`, sin `Access-Control-Allow-Credentials`. Repita con otro origen: debe responder `403` sin permitirlo.

CORS autoriza al navegador a leer la respuesta; no sustituye la autenticación. `GET /me` sin token debe responder `401`. `localhost`, `127.0.0.1` y puertos diferentes son orígenes distintos; configure únicamente los necesarios.

## 4. Comprobar el acceso real por correo

En PowerShell:

```powershell
$api = 'http://localhost:8080'
$email = Read-Host 'Correo institucional de prueba'
$request = @{ email = $email } | ConvertTo-Json
Invoke-RestMethod "$api/auth/code" -Method Post -ContentType 'application/json' -Body $request

# Abra http://localhost:8025 y consulte el código del correo recibido.
$code = Read-Host 'Código de seis dígitos recibido en Mailpit'
$verification = @{ email = $email; code = $code } | ConvertTo-Json
$session = Invoke-RestMethod "$api/auth/verify" -Method Post `
  -ContentType 'application/json' -Body $verification
$headers = @{ Authorization = "Bearer $($session.token)" }

if ($session.needsName) {
    $profile = @{ name = 'Persona de prueba' } | ConvertTo-Json
    Invoke-RestMethod "$api/me/profile" -Method Put -Headers $headers `
      -ContentType 'application/json' -Body $profile
}
Invoke-RestMethod "$api/me" -Headers $headers
Invoke-RestMethod "$api/rules" -Headers $headers
Invoke-RestMethod "$api/spaces" -Headers $headers
Invoke-RestMethod "$api/quota/me" -Headers $headers
Invoke-RestMethod "$api/reservations/mine" -Headers $headers
```

El código no es ficticio: debe coincidir con el correo. Por defecto, espere un minuto entre solicitudes al mismo correo. No imprima ni copie el token a logs o URLs. El logout registra la salida; el cliente debe eliminar su token y no hay renovación automática.

Mailpit captura los correos localmente: recibirlos allí no demuestra entrega a cuentas institucionales. Las URLs generadas pueden apuntar al frontend todavía apagado; aquí se verifica su contenido, no su navegación.

## 5. Datos y contratos para la futura integración

Una instalación nueva incluye parámetros de uso, pero no espacios ni implementos de ejemplo. Con una cuenta ADMIN cree los datos por los endpoints existentes; no inserte datos de demo en la migración compartida.

| Operación | Contrato |
|---|---|
| Crear espacio con imágenes | [`POST /admin/spaces`](contracts/spaces/POST-admin-spaces.md): multipart con parte JSON `space` y archivos obligatorios `preview` y `panorama`. |
| Crear implemento | [`POST /admin/equipment`](contracts/equipment/POST-admin-equipment.md) |
| Consultar disponibilidad | [`GET /spaces/{id}/availability`](contracts/availability/GET-spaces-id-availability.md) |
| Reservar como USER | [`POST /reservations`](contracts/reservations/POST-reservations.md) |
| Previsualizar/cancelar | [Contratos de reservas](contracts/README.md#reservas-uc03-y-uc04) |
| Consultar reservas para check-in | [`GET /checkin/{spaceId}/reservations`](contracts/checkin/GET-checkin-spaceId-reservations.md) |
| Confirmar asistencia elegida | [`POST /checkin/{spaceId}/reservations/{reservationId}`](contracts/checkin/POST-checkin-spaceId-reservations-reservationId.md) |
| Obtener QR como ADMIN | [`GET /admin/spaces/{id}/qr`](contracts/checkin/GET-admin-spaces-id-qr.md): SVG. |
| Consultar bloqueos e historial | [`GET /admin/blocks`](contracts/blocks/GET-admin-blocks.md): ADMIN, espacio/rango/estado y paginación, sin reautenticación de lectura. |
| Cancelar una franja institucional | [`POST /admin/blocks/{id}/cancellation`](contracts/blocks/POST-admin-blocks-id-cancellation.md): ADMIN reautenticado, motivo obligatorio y operación idempotente. |
| Contactar administración | [`POST /contact`](contracts/contact/POST-contact.md) |

Puntos que el cliente debe respetar:

- UUID en texto; instantes ISO-8601 con offset de Bogotá y fechas `YYYY-MM-DD`.
- El calendario abarca hoy hasta hoy + 6, inclusive, en `America/Bogota`. Use las reglas obtenidas por API y `bookable` de cada franja.
- Solo USER crea reservas. ADMIN conserva consultas y operaciones sobre sus propias reservas.
- Perfil incompleto: completar nombre antes de consultar el resto de la aplicación.
- `REAUTH_REQUIRED`: solicitar/verificar el código de reautenticación y usar el nuevo token para repetir una vez la acción sensible.
- Errores JSON `{ code, message, details }`; no confundir errores de red con listas vacías.
- Las imágenes requieren Bearer; descargarlas mediante HTTP autenticado, no depender de cookies.
- El QR utiliza el origen configurado y la ruta `/checkin/{spaceId}`.
- Al abrirlo, consultar las reservas propias elegibles y confirmar por identificador; refrescar la lista después. El check-in admite hasta fin + 1 hora inclusive y desde el fin responde `COMPLETED`, aunque otro usuario esté usando el espacio. El POST anterior por espacio se conserva, pero devuelve `CHECKIN_SELECTION_REQUIRED` si hay varias pendientes elegibles.
- La creación aplica el máximo fijo de 2 horas diarias entre todos los espacios, además de la cuota semanal. Leer `quota.dailyHours` y `checkIn.closesMinutesAfterEnd` en `/rules` y manejar `DAILY_QUOTA_EXCEEDED`; el calendario no calcula un saldo diario personal.
- SSE usa Bearer en encabezados: `EventSource` nativo no permite establecer `Authorization`. Usar un cliente compatible, nunca un token en query.
- El evento `availability-changed` invalida el calendario; consultar de nuevo. Cerrar el stream al abandonar la pantalla o sesión.
- Para cancelar bloqueos seleccionar el ID del listado administrativo, no el motivo del calendario. Pedir motivo y confirmación con franja exacta; advertir que las reservas canceladas no se reactivan y que la serie no se cancela. Mostrar historial con actor/fecha/motivo; `ACTIVE` incluye no cancelados ya terminados. Admitir reintentos sin sobrescribir la primera cancelación. Tras éxito refrescar listado, calendario y ocupación; no marcar libre optimistamente, porque puede haber otros impedimentos o mantenimiento. El cuerpo inválido sigue dando 400 y todo reintento requiere permisos/reautenticación. La integración móvil y accesible requiere el repositorio React.
- Al crear o editar espacios, no combinar `FOOTBALL_5` y `FOOTBALL_11`: el formulario
  lo impide y la API responde `400 VALIDATION_ERROR` con `details.fields.disciplines`.
  Las demás combinaciones de disciplinas siguen permitidas.

Para validar eventos en tiempo real sin cliente web, use `AvailabilityIntegrationTest`: abre el stream autenticado con un origen CORS permitido, crea un bloqueo y comprueba la recepción del evento confirmado.

D-27 incorpora invalidación al cancelar un bloqueo tras commit. `BlockCancellationIntegrationTest` comprueba SSE HTTP real, ausencia de evento confirmado por rollback/reintento, permisos, historial, concurrencia y no reactivación; `BlockCancellationMigrationTest` verifica preservación V1→V2 y restricciones. Los resultados completos se registran en el plan UC07, sin atribuirlos a ejecuciones históricas.

## 6. Verificación automatizada

Desde una consola sin configuración de producción heredada:

```powershell
Remove-Item Env:SPRING_CONFIG_IMPORT -ErrorAction SilentlyContinue
.\mvnw.cmd clean verify
```

Testcontainers crea PostgreSQL y Mailpit aislados; no requiere `.env` ni usa el volumen local de Compose. Los reportes vigentes quedan en `target/surefire-reports/`.

Cobertura relevante:

- `CorsConfigurationTest`: orígenes locales, lista de orígenes, preflight JSON/multipart/SSE, métodos y encabezados rechazados, sin cookies; no requiere Docker.
- `AuthSecurityTest`: seguridad, roles, sesión, CORS real y respuestas de autenticación legibles por el navegador.
- `AvailabilityIntegrationTest`: calendario y eventos SSE autenticados con CORS.
- `SpaceImageIntegrationTest`, `CheckInIntegrationTest`, contratos de reservas/cuota/reglas y pruebas de implementos: operaciones y recursos.
- `SmtpDeliveryTest` y `ContactSmtpIntegrationTest`: SMTP real capturado en Mailpit.
- `LoginSmtpIntegrationTest`: recorrido HTTP con origen local, código obtenido del SMTP real, perfil y recursos protegidos, sin sustituir el servicio de correo.
- `DevProfileTest` y `QrServiceTest`: aislamiento del acceso de desarrollo y restricciones del origen QR.

Ejecutar solo CORS, sin Docker:

```powershell
.\mvnw.cmd '-Dtest=CorsConfigurationTest' test
```

### Resultado verificado el 17 de noviembre de 2026

- `.\mvnw.cmd clean verify`: 275 pruebas, cero fallos, errores u omisiones; JAR generado correctamente.
- Después de añadir `LoginSmtpIntegrationTest`, `.\mvnw.cmd '-Dtest=LoginSmtpIntegrationTest' test`: una prueba adicional aprobada. Se verificaron solicitud de código, entrega SMTP real, login, restricción de perfil incompleto, actualización del perfil, consultas protegidas, logout y CORS local.
- `docker compose --env-file .env.example --profile app config --quiet`: configuración válida.

Las pruebas usaron bases y buzones aislados de Testcontainers. No se creó un `.env` con secretos, no se modificó ni validó una base local preexistente y no quedó una API de desarrollo ejecutándose en `8080`. El arranque habitual requiere completar las claves y el correo administrador según esta guía. No se modificó ni ejecutó el frontend.

### Resultado verificado el 24 de noviembre de 2026

- La primera ejecución de esta ampliación quedó bloqueada porque Testcontainers no encontraba Docker. La ejecución sin contenedores aprobó 43 pruebas unitarias seleccionadas; no acreditaba las integraciones.
- Tras iniciar Docker Desktop, `.\mvnw.cmd clean verify` terminó con `BUILD SUCCESS`: **336 pruebas, cero fallos, errores u omisiones**, y generó `target/demo-0.0.1-SNAPSHOT.jar`.
- Se ejecutaron las 12 pruebas de `CheckInIntegrationTest`, las 8 de `ReservationCreationIntegrationTest` y las 14 de `ReservationLifecycleIntegrationTest`, además de contratos, cuotas, parámetros, cancelación, seguridad y el resto de la suite.
- La cobertura incluye máximo diario acumulado y concurrencia, selección de reservas propias, asistencia tardía con otro ocupante, cierre inclusivo, reintentos sin duplicar auditoría y coordinación con inasistencias. Los reportes de esta ejecución están en `target/surefire-reports/`.
- PostgreSQL y Mailpit se ejecutaron en contenedores aislados de Testcontainers; no se modificó la base local ni se desplegó el frontend. Esto no acredita la aceptación con React, QR impreso, Supabase real ni entrega institucional mediante Brevo.

### Ampliación de correos y plantilla definitiva, verificada el 24 de noviembre de 2026

- `.\mvnw.cmd clean verify` aprobó **346 pruebas, sin fallos, errores ni omisiones**, y generó el JAR, con PostgreSQL y Mailpit aislados de Testcontainers. Esta ejecución posterior incluye los cambios de PDF y correos; los resultados de 336 y 345 pruebas anteriores se conservan como históricos. Una ejecución intermedia falló por la caída de contenedores de PostgreSQL, no por aserciones: se reinició Docker y se repitió la suite completa.
- `LoginSmtpIntegrationTest` solicita y verifica un código recibido por SMTP, completa el perfil, crea una reserva por la API y obtiene el correo de confirmación con alternativas HTML/texto plano y el PDF adjunto desde Mailpit. No se envió a un buzón institucional.
- `ReceiptPdfGeneratorTest` comprueba el hash de la plantilla original, los datos y horario de Bogotá, la ausencia de reglas/check-in en el PDF, el caso sin implementos y una lista de 35 implementos con continuación. Las reglas y la explicación del check-in se comprobaron en el correo, no en el adjunto.
- `NotificationEventsTest` valida los efectos de cancelación a tiempo, tardía y por suspensión en ambas alternativas. `SmtpMailSenderTest` verifica MIME alternativo con adjunto, nombre del remitente y reintentos; se prueban escape de HTML y ceros iniciales del código.
- Los ejemplos reproducibles quedan en `target/test-artifacts/`: `receipt-preview.pdf`, `receipt-many-items.pdf`, `reservation-delivered.pdf`, `reservation-confirmation.html` y `login-code.html`. El código `001234` del último archivo es ficticio, solo para vista previa. Los enlaces `localhost`/`client.example` son de pruebas, no direcciones públicas de despliegue.
- Se revisaron visualmente el PDF y los HTML renderizados localmente. La aceptación en Gmail/Outlook y en el buzón institucional con el proveedor real sigue pendiente; la vista local y Mailpit no acreditan entrega externa.

## 7. Cambiar de local a despliegue

- Inyecte secretos, PostgreSQL y correo externos mediante el entorno o gestor de secretos.
- Desactive `dev`; no copie la plantilla local como configuración de producción.
- Configure `APP_CORS_ALLOWED_ORIGINS` con los orígenes públicos exactos.
- Cambie `APP_CHECKIN_FRONTEND_ORIGIN`, `APP_MAIL_CONTACT_URL` y `APP_MAIL_CANCELLATION_URL` a URLs públicas HTTPS conservando las rutas acordadas.
- Configure remitente autorizado y transporte: en Render use Brevo HTTPS; si elige SMTP, configure autenticación y TLS según el proveedor.
- Termine HTTPS/HSTS en el punto de entrada. Configure confianza en proxies explícitamente, sin confiar indiscriminadamente en `X-Forwarded-For`.
- Si existe proxy, permita streaming SSE sin buffering ni timeouts incompatibles y cargas multipart de los tamaños admitidos.
- La publicación del frontend debe resolver rutas SPA como `/checkin/...` y `/reservations/.../cancel`. Esto corresponde a la futura sesión del cliente.

No hace falta modificar las rutas Java al cambiar de dominios. La conexión del frontend y la aceptación en navegador siguen siendo una validación separada.

## 8. Validar correo con Brevo

### HTTPS en Render

1. Verifique el remitente en Brevo y genere una **API key**, no una clave SMTP. Guárdela exclusivamente en el entorno del backend.
2. Configure `APP_MAIL_PROVIDER=brevo`, `APP_MAIL_BREVO_API_KEY`, `APP_MAIL_FROM` y `APP_MAIL_FROM_NAME`. Los timeouts opcionales `APP_MAIL_BREVO_CONNECT_TIMEOUT=PT5S` y `APP_MAIL_BREVO_REQUEST_TIMEOUT=PT10S` deben estar entre 1 ms y un minuto.
3. Configure PostgreSQL externo y `SPRING_DOCKER_COMPOSE_ENABLED=false`, las URLs HTTPS reales de contacto/cancelación y el destinatario de contacto. No active el perfil `dev` en Render. No necesita credenciales ni puertos SMTP.
4. Solicite y verifique un código institucional; cree una reserva y abra el PDF recibido. Pruebe contacto, cancelación y recordatorio. Compruebe HTML/texto, enlaces, nombre del remitente, registros de Brevo y buzón/spam institucional.
5. En pruebas aisladas, compruebe errores remotos: acceso/contacto deben conservar `503 MAIL_DELIVERY_FAILED` y un fallo del comprobante no debe revertir la reserva confirmada.

La API usada es `POST https://api.brevo.com/v3/smtp/email` por HTTPS/443 con `api-key`. Se acepta HTTP 201, sin reintentos automáticos ni redirecciones. Un timeout no prueba que Brevo haya rechazado el correo. Las pruebas automáticas simulan el proveedor localmente, sin usar claves reales ni acreditar recepción institucional. Render Free puede suspender el proceso y aplazar los jobs; HTTPS no elimina esa limitación.

### SMTP alternativo y Mailpit

Seleccione `APP_MAIL_PROVIDER=smtp` para los pasos siguientes. Es el modo predeterminado por compatibilidad; no es necesario para Render con HTTPS.

1. En Brevo, cree y verifique el remitente `unimagdeportes@gmail.com` con nombre `Unimag Deportes`. Gmail es un dominio de terceros: no se pueden configurar sus DNS y Brevo puede adaptar la dirección remitente. Compruebe las advertencias del panel y la recepción final; un buzón verificado no garantiza entrega institucional.
2. Genere una clave SMTP y guárdela exclusivamente en el entorno privado del backend. No use una API key ni la contraseña de la cuenta, y reemplace cualquier clave compartida públicamente.
3. Configure las variables SMTP descritas en el README: host `smtp-relay.brevo.com`, puerto `587`, usuario SMTP, clave SMTP, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true` y `MAIL_SMTP_STARTTLS_REQUIRED=true`. Establezca `APP_MAIL_FROM=unimagdeportes@gmail.com` y `APP_MAIL_FROM_NAME=Unimag Deportes`. La dirección sigue siendo simple, sin incluir el nombre entre comillas o corchetes angulares.
4. Para Maven/JAR, desactive la integración automática mediante `SPRING_DOCKER_COMPOSE_ENABLED=false`. Inicie PostgreSQL por separado y configure sus tres variables `SPRING_DATASOURCE_*`; si es el PostgreSQL de Compose publicado en el host, utilice el puerto real `POSTGRES_HOST_PORT`. Si usa `.env`, conserve la importación explícita `SPRING_CONFIG_IMPORT=file:.env[.properties]`. En el servicio `app` de Compose, las variables SMTP ya se transmiten al contenedor y la conexión de base de datos sigue apuntando a `postgres`.
5. Mantenga las URLs de contacto y cancelación accesibles desde el cliente. Reinicie el backend después de cambiar la configuración.
6. Compruebe conectividad al puerto 587 desde el servidor de despliegue, no solo desde el equipo local.
7. Solicite un código mediante `POST /auth/code` y complete el acceso con el código recibido; no use `/dev/login` para validar entrega. Compruebe el encabezado From, la vigencia y el enlace de contacto.
8. Cree una reserva y compruebe su PDF. Ejercite notificaciones y contacto con cuentas autorizadas, revisando enlaces, destinatarios y registros transaccionales de Brevo. La aceptación SMTP no demuestra recepción: revise el buzón institucional y spam, además del nombre y la dirección que muestra el cliente.
9. En un entorno de prueba, simule un fallo SMTP y confirme que acceso y contacto conservan `503 MAIL_DELIVERY_FAILED`, mientras el fallo de comprobantes o notificaciones no revierte la operación de negocio.

El envío sigue siendo de mejor esfuerzo, sin cola persistente ni garantía de ausencia de duplicados. No active logs de credenciales ni códigos de acceso. No se ha acreditado la recepción real con Brevo mediante las pruebas locales.

Para volver a Mailpit, seleccione `APP_MAIL_PROVIDER=smtp`, elimine las credenciales y desactive autenticación y ambas opciones STARTTLS. Fuera de Compose use `localhost` y el puerto publicado de Mailpit; dentro de `app` use el override `compose.mailpit.yaml`, que selecciona SMTP y `mailpit:1025`.

Antes de `.\mvnw.cmd clean verify`, use una consola sin importación de `.env` ni variables SMTP reales. Las pruebas usan PostgreSQL y Mailpit de Testcontainers y requieren Docker disponible; nunca deben utilizar la clave de Brevo.
