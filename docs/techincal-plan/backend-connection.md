# Backend preparado para la conexión del frontend

Esta guía prepara y comprueba la API sin ejecutar ni modificar el cliente React. La integración visual y la navegación del cliente se validarán por separado.

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
| Confirmar asistencia | [`POST /checkin/{spaceId}`](contracts/checkin/POST-checkin-spaceId.md) |
| Obtener QR como ADMIN | [`GET /admin/spaces/{id}/qr`](contracts/checkin/GET-admin-spaces-id-qr.md): SVG. |
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
- SSE usa Bearer en encabezados: `EventSource` nativo no permite establecer `Authorization`. Usar un cliente compatible, nunca un token en query.
- El evento `availability-changed` invalida el calendario; consultar de nuevo. Cerrar el stream al abandonar la pantalla o sesión.

Para validar eventos en tiempo real sin cliente web, use `AvailabilityIntegrationTest`: abre el stream autenticado con un origen CORS permitido, crea un bloqueo y comprueba la recepción del evento confirmado.

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

## 7. Cambiar de local a despliegue

- Inyecte secretos, PostgreSQL y SMTP externos mediante el entorno o gestor de secretos.
- Desactive `dev`; no copie la plantilla local como configuración de producción.
- Configure `APP_CORS_ALLOWED_ORIGINS` con los orígenes públicos exactos.
- Cambie `APP_CHECKIN_FRONTEND_ORIGIN`, `APP_MAIL_CONTACT_URL` y `APP_MAIL_CANCELLATION_URL` a URLs públicas HTTPS conservando las rutas acordadas.
- Configure remitente autorizado, autenticación y TLS SMTP según el proveedor.
- Termine HTTPS/HSTS en el punto de entrada. Configure confianza en proxies explícitamente, sin confiar indiscriminadamente en `X-Forwarded-For`.
- Si existe proxy, permita streaming SSE sin buffering ni timeouts incompatibles y cargas multipart de los tamaños admitidos.
- La publicación del frontend debe resolver rutas SPA como `/checkin/...` y `/reservations/.../cancel`. Esto corresponde a la futura sesión del cliente.

No hace falta modificar las rutas Java al cambiar de dominios. La conexión del frontend y la aceptación en navegador siguen siendo una validación separada.
