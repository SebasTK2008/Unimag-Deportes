# Implementation Plan: UnimagDeportes — Plataforma de reserva de canchas

**Date**: 2026-10-13
**Última revisión**: 2026-11-10 (cierre de los OQ, stock de implementos UC13, cliente React y MapStruct)
**Spec**: `docs/features/001-…` a `docs/features/013-…` (UC01–UC13). **Fuente funcional**: `docs/reglas-y-contexto/unimagDeportes.md`. Si un SPEC y el contexto discrepan, se consulta antes de decidir.
**Alcance de este documento**: solo la estructura general. Clases concretas, SQL exacto y contratos detallados van en `docs/features/<NNN>-<nombre>/plan.md` (ver §11).
**Leyenda**: **[CONTEXTO]** respaldado por `unimagDeportes.md` o los SPEC · **[TÉC]** decisión técnica del plan · **OQ-xx** punto resuelto con su decisión (§10) · **H1/H2** historia de usuario 1 o 2 dentro de un SPEC.

---

## Summary

UnimagDeportes es una aplicación web para reservar canchas de la universidad (baloncesto, voleibol, fútbol 5, fútbol 11 y tenis de campo) con disponibilidad en tiempo real, confirmación automática y reglas de uso aplicadas sin intervención del administrador. No hay cobros: el único control es la cuota semanal.

Se construye como **un monolito (un solo despliegue) con arquitectura de n capas**: `controller → service → repository → model`, con **PostgreSQL**. No hay colas de mensajería ni microservicios; el único sistema externo es el **servicio de correo**.

El acceso se hace con un **código de un solo uso enviado al correo institucional** y un **token JWT de 24 horas, sin cookies**: el cliente lo guarda en el almacenamiento del navegador y lo envía en cada petición con `Authorization: Bearer` (RN-49 a RN-58). No hay sesión en el servidor. Es un prototipo 100 % funcional pensado para ser **sustituido por el inicio de sesión de Microsoft** de la universidad sin tocar los casos de uso de negocio (§7.1, D-08, D-09).

---

## Technical Context

**Language/Version**: Java (LTS vigente) **[TÉC]**. El `pom.xml` actual usa Java 25.
**Primary Dependencies**: Spring Boot (estable vigente; el `pom.xml` actual usa 4.1.1): Web MVC (en Boot 4, `spring-boot-starter-webmvc`), Validation, Data JPA, Security (sin estado: soporte de *resource server* con JWT, sin cookies ni Spring Session; el nombre exacto del starter se confirma contra Boot 4), Mail, Scheduling, Actuator, Flyway **[TÉC]**. Lombok (ya en el `pom.xml`) y **MapStruct** (a agregar al `pom.xml` durante la implementación) para el mapeo DTO ↔ entidad (D-24). Librería de PDF a elegir en el plan de UC11.
**Storage**: PostgreSQL (`timestamptz`; extensión `btree_gist` para impedir cruces)
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL), servidor de correo de prueba (por ejemplo GreenMail). En desarrollo local, Mailpit para ver los correos reales que genera la aplicación.
**Target Platform**: Contenedores Docker; Docker Compose en desarrollo (PostgreSQL y Mailpit)
**Project Type**: Backend monolítico (API REST + SSE). El cliente web *mobile-first* se construye en **React** y vive en **otro repositorio**, por lo que consume la API desde otro origen con CORS configurado para el encabezado `Authorization` (OQ-01, OQ-13); el SSE se consume con `fetch` (§5.3).
**Constraints** **[CONTEXTO]**: cero cruces con verificación y bloqueo transaccional (RN-10); parámetros configurables, no fijos en el código (sección 4); fechas en America/Bogota (RNF-07); solo nombre y correo del usuario (RNF-09); auditoría de reservas, cancelaciones, check-ins, sanciones e inicios y cierres de sesión (RNF-08); reservar en 5 pasos o menos y en 40 s (RNF-01, RNF-03); *mobile-first* y WCAG (RNF-02, RNF-05); token JWT de 24 h sin cookies, estado del usuario consultado en cada petición, medidas contra XSS (RN-54 a RN-58, RNF-10)
**Scale/Scope**: 13 SPECs (UC01–UC13) con 23 historias de usuario; actores Usuario, Administrador, Servicio de correo y Sistema. Una sola universidad y volumen bajo: una instancia basta.

---

## 1. Alcance y trazabilidad SPEC → componentes

Cada SPEC agrupa los casos de uso del diagrama que comparten actor, datos y reglas; el contenido de cada caso quedó como una historia de usuario dentro de su SPEC (D-16).

| UC (SPEC) | Historias de usuario | Quién lo invoca | Canal |
|---|---|---|---|
| UC01 Iniciar sesión | H1 Acceder con correo institucional (código de un solo uso y token JWT) | Usuario, Administrador | REST (token Bearer) |
| UC02 Consultar disponibilidad | H1 Calendario en tiempo real | Usuario, Administrador; interno (`include` de UC03) | REST + SSE |
| UC03 Reservar espacio deportivo | H1 Reservar una o dos franjas | Usuario | REST |
| UC04 Gestionar mis reservas | H1 Consultar · H2 Cancelar | Usuario | REST |
| UC05 Confirmar asistencia (QR) | H1 Check-in · H2 Generar e imprimir QR | Usuario · Administrador | REST (enlace del QR) |
| UC06 Reglas de uso | H1 Consultar · H2 Configurar | Usuario · Administrador | REST |
| UC07 Gestionar espacios y franjas | H1 Espacios y horario · H2 Bloquear franjas | Administrador | REST |
| UC08 Consultar cuota semanal | H1 Ver cuota · H2 Reinicio semanal | Usuario · Sistema | REST (H1); derivado (H2, ver D-06) |
| UC09 Consultar panel de administración | H1 Ocupación · H2 Indicadores | Administrador | REST |
| UC10 Aplicar sanciones | H1 Inasistencia · H2 Suspensión | Sistema | Scheduler |
| UC11 Enviar correos al usuario | H1 Comprobante PDF · H2 Notificaciones · H3 Código de acceso | Interno (H1 es `include` de UC03) · Sistema | Servicio de correo |
| UC12 Contactar administrador | H1 Enviar mensaje | Usuario | REST |
| UC13 Solicitar implementos extras | H1 Solicitar implementos al reservar · H2 Gestionar el catálogo | Usuario; Administrador | REST |

**Relaciones del diagrama UML respetadas** (diagrama de casos de uso del proyecto, no incluido en el repositorio): `include`: UC03→UC02 y UC03→UC11-H1 (comprobante). Los 11 casos del diagrama quedan cubiertos: Iniciar sesión (UC01), Consultar disponibilidad (UC02), Reservar espacio (UC03), Consultar mis reservas y Cancelar reserva (UC04), Confirmar asistencia (UC05-H1), Consultar y Configurar reglas (UC06), Gestionar espacios y Bloquear franjas (UC07), Enviar comprobante (UC11-H1). Provienen del contexto y aún no están en el diagrama: UC05-H2, UC08, UC09, UC10, UC11-H2, UC11-H3, UC12 y UC13 (H1 y H2).

---

## 2. Principios rectores

1. **Cero cruces**: un espacio nunca tiene dos reservas activas en la misma franja. La verificación y el bloqueo ocurren en una sola transacción, respaldada por una restricción en la base de datos (RN-10).
2. **Reglas configurables**: los parámetros **de negocio** de la sección 4 viven en la base de datos, nunca en el código; los parámetros de autenticación viven en `application.properties` (D-20).
3. **Las mismas reglas para todos** los Usuarios (RN-02); no hay cobros monetarios (principio 1).
4. **La identidad sale siempre del token verificado y del estado actual en la base de datos**, nunca del cuerpo, la URL ni el QR (RN-03, RN-32).
5. **Datos mínimos del Usuario**: nombre y correo institucional (RNF-09).
6. **Toda fecha y hora en America/Bogota**, con un `Clock` inyectado (RNF-07).
7. **La cuota se deriva de las reservas**, no se almacena (RN-16, RN-17, RN-19).
8. **Un fallo de correo no deshace una operación de negocio** **[TÉC]**. Excepción: el código de acceso. Sin correo no hay ingreso, así que se informa al Usuario y se le deja reintentar.
9. **Los estados de la reserva son los de la sección 6 del contexto** y solo cambian a través de los services.
10. **El mecanismo de autenticación es sustituible**: fuera de `security` y del módulo `auth`, nadie sabe cómo se autenticó la persona. El resto del sistema solo ve un `UserPrincipal(id, email, role)` (D-08, D-09).
11. **El token no congela permisos**: la existencia, el rol y la suspensión se leen de la base de datos en cada petición (RN-58), y todo token tiene una vigencia fija de 24 horas, sin renovación (D-17 a D-19).

---

## 3. Arquitectura

### 3.1 Vista de contexto

```mermaid
flowchart LR
  subgraph EXT["Actores y sistemas externos"]
    USR["Usuario (celular)"]
    ADM["Administrador"]
    MAIL["Servicio de correo"]
  end

  DB[("PostgreSQL")]

  subgraph SYS["UnimagDeportes (un monolito)"]
    CTL["controller"] --> SRV["service"] --> REP["repository"] --> MOD["model"]
  end

  SCH["scheduler (jobs internos)"] --> SRV
  USR -- "REST + SSE, token Bearer (sin cookies)" --> CTL
  USR -- "escanea QR: /checkin/{id_espacio}" --> CTL
  ADM -- "REST, token Bearer" --> CTL
  REP --> DB
  SRV -- "puerto MailSender (SMTP)" --> MAIL
```

### 3.2 Decisión de capas: n capas simples en un solo módulo

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Controller | `controller` | Entrada HTTP (REST y SSE): valida el formato, mapea DTO y exige el rol. Sin lógica de negocio. |
| Service | `service` | Casos de uso y reglas de negocio; define las transacciones. Las reglas puras viven en `service/policy`. |
| Repository | `repository` | Acceso a datos (Spring Data JPA; consultas nativas donde haga falta). |
| Model | `model` | Entidades JPA y enumeraciones. |

Paquetes transversales: `dto`, `mapper`, `security`, `mail`, `scheduler`, `exception`, `config`.

Dentro de cada capa los archivos se agrupan por **módulo de negocio** (`auth`, `space`, `reservation`, `checkin`, `block`, `parameter`, `quota`, `notification`, `equipment`, `report`, `contact`). Los módulos se hablan **solo mediante sus services**, de modo que si algún día hay que separar uno, la frontera ya existe.

| Concepto compartido | SPEC que lo tocan |
|---|---|
| `Reservation` (estado, horario, check-in) | UC03 la crea; UC04 la lista (H1) y la cancela (H2); UC05-H1 registra el check-in; UC07-H2 la cancela por bloqueo; UC10 la marca inasistencia o completada; UC08, UC09 y UC02 la leen |
| `UsageParameters` | UC06-H2 la escribe; UC01, UC03, UC04, UC05, UC06-H1, UC08, UC10 y UC11 la leen |
| `Space` (y sus franjas derivadas) | UC07-H1 lo escribe; UC02, UC03, UC05-H2 (QR) y UC09 lo leen |
| `InstitutionalBlock` | UC07-H2 lo crea; UC02, UC03 y UC09 lo leen |
| `AppUser` (incluye suspensión) | UC01 lo crea; UC10-H2 lo suspende; UC03 valida su estado; el filtro de `security` lo lee en cada petición |
| Token JWT (`TokenService`) | UC01 lo emite; el filtro de `security` lo verifica en cada petición. No se guarda en el servidor |
| `MailSender` (correo saliente) | UC01 (código de acceso), UC11 (comprobante y notificaciones) y UC12 |
| `Equipment` / `ReservationEquipment` | UC13 mantiene el catálogo (Administrador); UC03 asocia los implementos solicitados a la reserva |

### 3.3 Estructura del proyecto

```text
Unimag-Deportes/
├── docs/
│   ├── techincal-plan/plan-general.md           # Este documento
│   ├── reglas-y-contexto/unimagDeportes.md
│   ├── uc-diagrams/                            # diagrama UML (no incluido en el repositorio)
│   └── features/NNN-<uc>/{spec.md, plan.md}
├── src/main/java/unimagdeportes/              # paquete raíz (OQ-01)
│   ├── Application.java
│   ├── controller/       # auth, availability, reservation, checkin, space, block, parameter, quota, report, contact, equipment
│   ├── service/          # un service por tema
│   │   └── policy/       # QuotaPolicy, CancellationPolicy, CheckInWindowPolicy, BookingWindowPolicy, WeekCalculator,
│   │                     # ReauthPolicy, LoginCodePolicy
│   ├── repository/       # un repositorio por entidad
│   ├── model/            # AppUser, LoginCode, Space, Reservation, InstitutionalBlock, Equipment,
│   │                     # ReservationEquipment, UsageParameters, AuditLog + enums (ReservationStatus, Discipline, BlockType, Role)
│   ├── dto/              # request/response (las entidades no se exponen)
│   ├── mapper/           # MapStruct: DTO ↔ entidad (D-24)
│   ├── security/         # JwtAuthenticationFilter, UserPrincipal, SecurityConfig (sin estado, sin CSRF, CORS, CSP),
│   │                     # control de roles y de reautenticación, límite de solicitudes y de intentos de código
│   ├── mail/             # MailSender (puerto), SmtpMailSender, plantillas, ReceiptPdfGenerator
│   ├── scheduler/        # ReservationLifecycleJob, ReminderJob, AuthCleanupJob
│   ├── exception/        # BusinessException y derivadas + GlobalExceptionHandler
│   └── config/           # Spring, Clock (America/Bogota), seguridad, SSE
├── src/main/resources/{application.properties, db/migration/V*.sql}
├── src/test/java/…       # espejo de paquetes
├── Dockerfile
├── compose.yaml          # PostgreSQL y Mailpit (desarrollo)
└── pom.xml
```

### 3.4 Reglas de dependencia

1. `controller` solo invoca `service` y usa `dto`; nunca `repository` ni `model`.
2. `service` usa `repository`, `model` y otros `service`. **No usa el repository de otro módulo**: pide al service de ese módulo.
3. `repository` solo conoce `model`.
4. Las entidades JPA no salen del service: los controllers reciben y devuelven DTO.
5. `service/policy` es **puro** (sin Spring ni JPA): recibe valores y devuelve decisiones, y se prueba con pruebas unitarias.
6. Controller, filtro de seguridad y scheduler son puntos de entrada: todos llaman a services.
7. Ningún componente lee la hora del sistema: todos usan el `Clock` inyectado.
8. Opcional: verificar las reglas 1 a 4 y la 9 con ArchUnit en las pruebas.
9. **Aislamiento de la autenticación**: solo `security` y los services del módulo `auth` (`AuthService`, `LoginCodeService`, `TokenService`, `UserService`) conocen cómo se autenticó la persona. Los demás controllers y services reciben un `UserPrincipal` y nunca tocan códigos ni tokens.

### 3.5 Mapa SPEC → componentes (semilla de los planes específicos)

| UC | Controller / entrada | Services principales | Persistencia | Invariantes clave |
|---|---|---|---|---|
| UC01 | `AuthController` (incluye `/me`) | `AuthService`, `LoginCodeService`, `TokenService`, `UserService` (`resolveOrCreate(email, name)`) | `app_user`, `login_code`, `audit_log` (sin tabla de sesiones) | Dominio institucional (parámetro) validado en el servidor sobre el correo completo (RN-50); código de 6 dígitos guardado como HMAC, de un solo uso, 10 min de vigencia, 5 intentos y el nuevo invalida el anterior (RN-51, RN-52); mínimo 60 s entre solicitudes y máximo 5 por hora por correo, más un límite por IP (RN-53), y misma respuesta exista o no la cuenta; JWT firmado (HS256, algoritmo fijado en el servidor, llave en variable de entorno) con `sub`, `email`, `role`, `iat` y `exp` = 24 h, sin renovación (RN-54, RN-56); sin cookies: el cliente lo guarda y lo envía en `Authorization: Bearer` (RN-55); en cada petición se consulta `app_user` (existencia, rol, suspensión) y no se confía en el token para eso (RN-58); el nombre se pide solo en el primer ingreso (RN-59; sin nombre solo se permiten `/me` y `/auth/*`); el Usuario suspendido puede iniciar sesión (RN-05); token vencido → `401 TOKEN_EXPIRED` y el cliente vuelve a la pantalla de origen tras el login (RN-56); reautenticación del Administrador en acciones sensibles. Detalle en §7.1 |
| UC02 | `AvailabilityController` | `AvailabilityService` | lectura de `space`, `reservation`, `institutional_block` | Franja libre, ocupada o bloqueada; el espacio compartido se ocupa completo (RN-07); mantenimiento no reservable (RN-09); franja iniciada reservable hasta fin de tolerancia (RN-27, RN-28); motivo visible en bloqueos (RN-44); cambios por SSE (el cliente usa `fetch`, no `EventSource`, ver §5.3); consulta sin sesión: no (OQ-14); horizonte del calendario: 7 días, alineado con la anticipación máxima (OQ-09) |
| UC03 | `ReservationController` | `ReservationService`, `QuotaService` | `reservation` | Una transacción: bloquear espacio → validar → insertar; 1 o 2 franjas consecutivas; hasta 7 días (OQ-12 cerrado); sin solape propio (RN-11); cuota (4 h por defecto, configurable); no suspendido; comprobante después del commit (UC11-H1) |
| UC04 | `ReservationController` | H1 `ReservationQueryService` · H2 `CancellationService`, `CancellationPolicy` | `reservation` | H1: solo las reservas del Usuario del token, todos los estados, acción de cancelar. H2: vista previa con efecto en la cuota (RN-25); 60 min o más = a tiempo, menos = tardía (umbral configurable); no se cancela con check-in (RN-24); notifica (RN-26); no hay cancelación manual del Administrador |
| UC05 | `CheckInController` · `SpaceController` (QR, admin) | H1 `CheckInService`, `CheckInWindowPolicy` · H2 `QrService` | `reservation` (H1); H2 se deriva del id del espacio | H1: el Usuario del token debe ser el dueño de la reserva en ese espacio (RN-33); sin token vigente (o expirado), el cliente lleva al login y vuelve a `/checkin/{id_espacio}`; si el QR se abre en otro navegador no hay token y se inicia sesión de nuevo (RN-34); ventana desde 10 min antes hasta fin de tolerancia; reservas hechas en tolerancia: 10 min desde su creación (RN-30); motivo de rechazo visible (RN-35). H2: QR estático con enlace fijo `/checkin/{id_espacio}`, sin datos del Usuario (RN-31, RN-32); imprimible |
| UC06 | `ParameterController` (H1 Usuario, H2 admin) | `ParameterService` | `usage_parameters` | H1: lenguaje claro, valores vigentes, sin números fijos en el código. H2: solo Administrador **con reautenticación reciente**; edita los parámetros de la sección 4 (sin cobros); los cambios rigen solo para operaciones nuevas (OQ-05); los parámetros de autenticación quedan fuera de `usage_parameters` (OQ-16) |
| UC07 | `SpaceController` (H1, admin) · `BlockController` (H2, admin) | H1 `SpaceService` · H2 `BlockService` | `space`, `space_discipline`, `institutional_block`, `reservation` | H1: atributos de RN-06; franjas de 60 min derivadas del horario de operación (RN-08); mantenimiento no reservable. H2: exige reautenticación reciente (cancela reservas en masa); prioridad sobre reservas libres (RN-42); cancela las afectadas con estado `CANCELLED_BY_ADMIN`, no cuentan cuota y avisa con motivo (RN-43); los bloqueos recurrentes se materializan en una fila por franja (OQ-06); bloquea el espacio durante la operación |
| UC08 | `QuotaController` | `QuotaService`, `WeekCalculator` | lectura de `reservation` | H1: horas usadas = reservas que cuentan en la semana (lunes 00:00 America/Bogota); disponibles = cuota − usadas; fecha de reinicio = próximo lunes. H2: sin job; la cuota se calcula sobre la semana de la fecha de la franja (RN-17) |
| UC09 | `ReportController` (admin) | H1 `OccupancyService` · H2 `IndicatorService` | consultas sobre `reservation`, `institutional_block`, `space`, `app_user` | Solo lectura; H1 con filtros por fecha, disciplina y espacio; H2: reservas, cancelaciones tardías, inasistencias y suspendidos, con períodos fijos semanal y mensual seleccionables en el frontend (OQ-08) |
| UC10 | `ReservationLifecycleJob` | `SanctionService` | `reservation`, `app_user` | Sin check-in al cerrar la ventana → `NO_SHOW` con horas contadas; 3 en 30 días (ventana rodante) → suspende 7 días y reinicia el contador; reservas futuras se mantienen (RN-37 a RN-40, OQ-07) |
| UC11 | llamadas internas · `ReminderJob` | H1 `ReceiptService` · H2 `NotificationService` + `MailSender` | `reservation.reminder_sent_at` | H1: PDF con espacio, fecha, hora, nombre, implementos solicitados, reglas de cancelación y canal de contacto (RN-45, RN-48, OQ-20), al correo del Usuario. H2: recordatorio 2 h antes con enlace para cancelar (RN-46); cancelación, bloqueo, inasistencia y suspensión (RN-47); todos con canal de contacto (RN-48); falla de entrega: se registra y no revierte la operación (OQ-04) |
| UC12 | `ContactController` | `ContactService` + `MailSender` | — | Envía el mensaje al correo del área de deportes; sin persistir; es el canal de contacto de RN-48 (OQ-10) |
| UC13 | `EquipmentController` (admin) · `ReservationController` (solicitud) | `EquipmentService` · `ReservationService` | `equipment`, `reservation_equipment` | Catálogo con nombre, disciplina sugerida, existencias y estado; solo los implementos disponibles con stock suficiente se pueden solicitar; la solicitud es opcional, con cantidades, y se valida contra las reservas solapadas en la misma transacción (RN-62 a RN-66) |

---

## 4. Modelo de datos

### 4.1 Modelo de datos (PostgreSQL)

Convenciones: UUID como identificadores, `timestamptz` para instantes (se interpretan en America/Bogota), enums como texto, migraciones versionadas con Flyway. **No hay tabla de franjas, de cuota ni de sesiones**: las dos primeras se derivan (D-06) y la sesión vive en el token JWT, que el servidor no guarda (D-17).

| Tabla | Propósito / columnas relevantes | Restricciones |
|---|---|---|
| `app_user` | `id`, `email`, `name` (vacío hasta el primer ingreso), `suspended_until`, `role` **[TÉC]**, `created_at` | `UNIQUE(email)` guardado en minúsculas y sin espacios; solo nombre y correo como datos personales (RNF-09) |
| `login_code` | `id`, `email`, `code_hash` (HMAC con clave del servidor), `expires_at`, `attempts`, `used_at`, `created_at` | Un solo uso; al pedir un código nuevo se invalidan los anteriores del correo; las solicitudes por correo se cuentan con `created_at` (60 s entre solicitudes y 5 por hora); `AuthCleanupJob` borra los vencidos |
| `space` | `id`, `name`, `capacity`, `requirements`, `opening_time`, `closing_time` **[TÉC]**, `status` (`ACTIVE`, `MAINTENANCE`) | RN-06 |
| `space_discipline` | `space_id`, `discipline` (baloncesto, voleibol, fútbol 5, fútbol 11, tenis de campo) | `PRIMARY KEY(space_id, discipline)` |
| `reservation` | `id`, `user_id`, `space_id`, `start_at`, `end_at`, `status`, `created_at`, `checked_in_at`, `reminder_sent_at` | `CHECK(end_at > start_at)`; **restricción de exclusión**: mismo `space_id` con rangos que se solapan solo entre estados que no liberan el espacio; **segunda exclusión** equivalente por `user_id` (RN-11) |
| `institutional_block` | `id`, `space_id`, `type` (clases y grupos formativos, eventos o torneos, mantenimiento), `reason`, `start_at`, `end_at`, `recurrence_group` (nullable; agrupa las ocurrencias de un bloqueo recurrente) | Índice por `(space_id, start_at)`; los bloqueos recurrentes se materializan en una fila por franja (OQ-06) |
| `equipment` | `id`, `name`, `discipline` (nullable, sugerencia), `stock`, `status` (`AVAILABLE`, `INACTIVE`) | `UNIQUE(name)`; `CHECK(stock >= 0)`; RN-62 |
| `reservation_equipment` | `reservation_id`, `equipment_id`, `quantity` | `PRIMARY KEY(reservation_id, equipment_id)`; `CHECK(quantity > 0)`; claves foráneas a `reservation` y `equipment`; RN-64 |
| `usage_parameters` | Singleton (`id = 1`): una columna por cada parámetro **de negocio** de la sección 4 | `CHECK` de rangos; cada guardado sobrescribe |
| `audit_log` | `id`, `action`, `entity_type`, `entity_id`, `user_id` (nullable), `detail`, `created_at` | RNF-08; sin códigos ni tokens en `detail`. Además de reservas, cancelaciones, check-ins y sanciones, registra `CODE_REQUESTED`, `LOGIN`, `LOGIN_FAILED` y `LOGOUT` (RN-61) |

**Estados de la reserva que no liberan el espacio** (participan en la restricción de exclusión): `CONFIRMED`, `IN_USE`, `COMPLETED`, `NO_SHOW`. Las tres cancelaciones liberan. Esa misma lista determina qué reservas consumen stock de implementos (RN-65, RN-66).

**Por qué dos mecanismos para no cruzar** (D-04, D-05): la restricción de exclusión impide dos reservas solapadas en la tabla `reservation`. Como los bloqueos viven en otra tabla, además se bloquea la fila del espacio durante las operaciones de reservar y bloquear.

### 4.2 Diagrama Entidad-Relación

```mermaid
erDiagram
    app_user {
        uuid id PK
        string email
        string name
        datetime suspended_until
        string role
        datetime created_at
    }
    login_code {
        uuid id PK
        string email
        string code_hash
        datetime expires_at
        int attempts
        datetime used_at
        datetime created_at
    }
    space {
        uuid id PK
        string name
        int capacity
        string requirements
        time opening_time
        time closing_time
        string status
    }
    space_discipline {
        uuid space_id PK, FK
        string discipline PK
    }
    reservation {
        uuid id PK
        uuid user_id FK
        uuid space_id FK
        datetime start_at
        datetime end_at
        string status
        datetime created_at
        datetime checked_in_at
        datetime reminder_sent_at
    }
    equipment {
        uuid id PK
        string name
        string discipline "nullable"
        int stock
        string status
    }
    reservation_equipment {
        uuid reservation_id PK, FK
        uuid equipment_id PK, FK
        int quantity
    }
    institutional_block {
        uuid id PK
        uuid space_id FK
        string type
        string reason
        datetime start_at
        datetime end_at
        string recurrence_group
    }
    usage_parameters {
        int id PK
    }
    audit_log {
        uuid id PK
        string action
        string entity_type
        uuid entity_id
        uuid user_id
        string detail
        datetime created_at
    }

    app_user ||--o{ reservation : crea
    space ||--o{ space_discipline : sirve
    space ||--o{ reservation : recibe
    space ||--o{ institutional_block : bloquea
    reservation ||--o{ reservation_equipment : incluye
    equipment ||--o{ reservation_equipment : solicitado
```

`login_code` no se relaciona por clave foránea con `app_user`: el código se pide antes de que la cuenta exista (la cuenta se crea al verificar el primer código). No hay tabla de sesiones: el token JWT es autocontenido y se valida por su firma y su fecha de expiración.

---

## 5. Conexiones y procesos internos

### 5.1 Matriz de conexiones

| # | Origen → Destino | UC | Estilo | Transporte |
|---|---|---|---|---|
| C1 | Usuario o Administrador → sistema | UC01 a UC09, UC12, UC13 | Request/response | REST con `Authorization: Bearer` |
| C2 | Usuario o Administrador → sistema | UC02 | Actualización en vivo | SSE (ver §5.3) |
| C3 | Usuario → sistema | UC05-H1 | Enlace fijo del QR | `/checkin/{id_espacio}` (ruta del cliente) que llama al endpoint de check-in con el token; sin token vigente, el cliente lleva al login y regresa; en otro navegador se inicia sesión de nuevo |
| C4 | Sistema → Servicio de correo | UC01, UC11, UC12 | Envío | SMTP mediante el puerto `MailSender` |
| C5 | Sistema → sistema | UC10, UC11-H2, limpieza de UC01 | Eventos por tiempo | Scheduler |

El sistema **no usa colas de mensajería**: los correos se envían después del commit mediante un evento interno. El código de acceso es la excepción: se envía dentro de la solicitud, porque el Usuario espera el correo para continuar y debe saber si falló.

### 5.2 Jobs internos programados

| Job | Origen | Regla |
|---|---|---|
| `ReservationLifecycleJob` (cada minuto) | UC10 y sección 6 del contexto | (a) Reservas `CONFIRMED` sin check-in cuya ventana cerró pasan a `NO_SHOW`, se evalúa la suspensión y se notifica. (b) Reservas `IN_USE` cuya franja terminó pasan a `COMPLETED`. |
| `ReminderJob` (cada minuto) | UC11-H2 | Reservas `CONFIRMED` que empiezan en 2 horas o menos y con `reminder_sent_at IS NULL`: envía el recordatorio y marca la fecha. |
| `AuthCleanupJob` (cada hora) | UC01 | Borra códigos de acceso vencidos o usados **[TÉC]**. |

Los tres jobs son idempotentes (se apoyan en el estado y en `reminder_sent_at`). **El reinicio semanal (UC08-H2) no tiene job**: ver D-06. Con una sola instancia no hace falta bloqueo de jobs; si algún día se despliegan varias, habrá que agregarlo.

### 5.3 Calendario en vivo

`GET /spaces/{id}/availability/stream` (SSE) avisa al cliente cuando cambia la disponibilidad del espacio (reserva, cancelación, bloqueo, cambio de estado). El cliente vuelve a pedir el calendario.

**Efecto de no usar cookies**: el `EventSource` nativo del navegador no permite enviar el encabezado `Authorization`. El cliente debe abrir el flujo con `fetch` en modo *streaming* (o una librería de SSE que acepte encabezados). **No se debe pasar el token en la URL** (`?token=`), porque quedaría en los registros de acceso y del proxy. Alternativa si esto complica el cliente: *polling* corto (D-11).

### 5.4 Flujo de referencia: reservar → comprobante

```mermaid
sequenceDiagram
  autonumber
  participant U as Usuario
  participant C as ReservationController
  participant S as ReservationService
  participant DB as PostgreSQL
  participant R as ReceiptService
  participant M as Servicio de correo

  U->>C: POST /reservations (espacio, inicio, número de franjas) + Bearer
  C->>S: crear reserva (usuario tomado del token verificado)
  S->>DB: bloquea el espacio (SELECT FOR UPDATE)
  S->>S: valida suspensión, anticipación y tolerancia, bloqueos, solape propio y cuota
  S->>DB: inserta la reserva CONFIRMED (restricción anti-cruce)
  S-->>C: reserva confirmada (commit)
  C-->>U: confirmación en pantalla
  S--)R: evento después del commit
  R->>M: comprobante PDF al correo institucional
```

### 5.5 Flujo de referencia: iniciar sesión con código

```mermaid
sequenceDiagram
  autonumber
  participant U as Usuario (cliente web)
  participant C as AuthController
  participant A as AuthService
  participant T as TokenService
  participant DB as PostgreSQL
  participant M as Servicio de correo

  U->>C: POST /auth/code (correo, la interfaz completa el dominio)
  C->>A: solicitar código
  A->>A: valida dominio contra el parámetro y los límites de solicitud
  A->>DB: guarda el HMAC del código (10 min, 5 intentos)
  A->>M: envía el código solo si el dominio es permitido
  C-->>U: 202 con la misma respuesta exista o no la cuenta
  U->>C: POST /auth/verify (correo, código)
  C->>A: verificar código
  A->>DB: valida el código y lo marca como usado
  A->>DB: resolveOrCreate(correo)
  A->>T: emitir token para el usuario
  T-->>A: JWT firmado (24 h)
  C-->>U: 200 con el token en el cuerpo (sin Set-Cookie) + needsName
  U->>U: guarda el token en el almacenamiento del navegador y vuelve a la pantalla de origen
```

Si la persona escaneó un QR sin token vigente (RN-34), el cliente guarda `/checkin/{id_espacio}` como destino de retorno, hace este flujo en la misma pestaña y vuelve al check-in. Lo mismo ocurre cuando el token expira (RN-56): el cliente recuerda la pantalla actual, pide iniciar sesión y regresa. Si el QR se abre en un navegador distinto al de la sesión, ese navegador no tiene el token y se inicia sesión de nuevo.

---

## 6. Contratos (índice de endpoints) **[TÉC]**

Este índice solo fija las rutas y los roles. El detalle de cada contrato (solicitud, respuesta y errores) se escribe en el `plan.md` de cada SPEC. Todas las rutas, salvo las públicas, exigen `Authorization: Bearer <token>`.

| Método y ruta | UC | Rol |
|---|---|---|
| `POST /auth/code` | UC01 | Público (con límite de solicitudes) |
| `POST /auth/verify` (devuelve el token) | UC01 | Público (con límite de intentos) |
| `GET /me` | UC01 | Autenticado |
| `PUT /me/profile` (nombre en el primer ingreso) | UC01 | Autenticado |
| `POST /auth/logout` (solo registra el cierre en auditoría; el cliente elimina el token) | UC01 | Autenticado |
| `POST /auth/reauth/code`, `POST /auth/reauth/verify` (devuelve un token con `reauth_at`) | UC01 | Administrador |
| `GET /spaces?discipline=` | UC02, UC03 | Usuario, Administrador |
| `GET /spaces/{id}/availability?date=` | UC02 | Usuario, Administrador |
| `GET /spaces/{id}/availability/stream` (SSE) | UC02 | Usuario, Administrador |
| `POST /reservations` | UC03 | Usuario |
| `GET /reservations/mine` | UC04-H1 | Usuario |
| `GET /reservations/{id}/cancellation-preview` | UC04-H2 | Usuario |
| `POST /reservations/{id}/cancellation` | UC04-H2 | Usuario |
| `POST /checkin/{spaceId}` | UC05-H1 | Usuario |
| `GET /admin/spaces/{id}/qr` | UC05-H2 | Administrador |
| `GET /rules` | UC06-H1 | Usuario |
| `GET /admin/parameters`, `PUT /admin/parameters` | UC06-H2 | Administrador (el `PUT` exige reautenticación reciente) |
| `GET/POST/PUT /admin/spaces` | UC07-H1 | Administrador |
| `POST /admin/blocks` | UC07-H2 | Administrador (exige reautenticación reciente) |
| `GET /quota/me` | UC08-H1 | Usuario |
| `GET /admin/occupancy` | UC09-H1 | Administrador |
| `GET /admin/indicators` | UC09-H2 | Administrador |
| `POST /contact` | UC12 | Usuario |
| `GET /equipment?discipline=` | UC13 | Usuario, Administrador |
| `GET /equipment/availability?startAt=&endAt=` | UC13 | Usuario, Administrador |
| `GET/POST/PUT /admin/equipment` | UC13 | Administrador |

Sin endpoint propio: UC08-H2, UC10 y UC11 (internos). `POST /reservations` acepta, de forma opcional, una lista de implementos solicitados con su cantidad y valida el stock disponible en la franja (UC13). `GET /equipment/availability` evalúa la ventana `startAt`–`endAt` solicitada (una franja o un rango) y devuelve la disponibilidad por solape con las reservas activas (RN-65). Los bloqueos recurrentes se fijan al crearse: no hay edición por `recurrence_group` en el prototipo; para cambiarlos se crea un bloqueo nuevo.

Códigos de error de autenticación: `401 TOKEN_EXPIRED` (el cliente pide iniciar sesión y regresa a la pantalla de origen), `401 TOKEN_INVALID`, `403 PROFILE_INCOMPLETE`, `403 REAUTH_REQUIRED`.

Con Microsoft (§7.1.5), `/auth/code`, `/auth/verify` y `/auth/reauth/*` se reemplazan por el flujo de inicio de sesión de Microsoft, tras el cual el backend emite el mismo JWT. `/me`, `/auth/logout` y todos los endpoints de negocio no cambian.

---

## 7. Aspectos transversales

### 7.1 Seguridad

#### 7.1.1 Login por código al correo institucional **[CONTEXTO, RN-49 a RN-53]**

**Flujo**
1. El Usuario escribe solo su usuario (`jperez`) y la interfaz completa el dominio. **El servidor valida el correo completo** (un solo `@`, dominio exacto contra el parámetro de UC06-H2) y no confía en lo que hizo la interfaz.
2. Si el dominio es permitido, se genera un código de 6 dígitos con un generador seguro, se guarda su HMAC y se envía por correo. Si no lo es, se rechaza y no se envía nada. La respuesta es la misma haya o no cuenta previa.
3. El Usuario escribe el código (campo con `autocomplete="one-time-code"` para que el celular lo sugiera). Se valida que esté vigente, sin usar y con menos de 5 intentos, y se marca como usado.
4. `UserService.resolveOrCreate(email, name)` ubica o crea el `app_user`; `TokenService` emite el JWT y lo devuelve en el cuerpo de la respuesta. El cliente lo guarda en el almacenamiento del navegador y vuelve a la pantalla de origen.

**Por qué código y no enlace mágico**: el flujo del QR (RN-34) empieza en el celular, y el enlace de un correo suele abrirse en otro navegador o en un WebView. Como el token no se guarda en cookies, ese navegador no tendría la sesión ni el destino. Con código, todo ocurre en la misma pestaña.

**Primer ingreso**: el código no trae el nombre y el comprobante lo necesita (RN-45, RN-59). Se pide una sola vez (`PUT /me/profile`); mientras falte, el filtro solo deja pasar `/me` y `/auth/*`, y los demás endpoints responden `403 PROFILE_INCOMPLETE`. Con Microsoft el nombre vendrá en el token y este paso desaparece.

| Regla del código | Valor |
|---|---|
| Formato | 6 dígitos, generador criptográficamente seguro **[CONTEXTO, propuesto]** |
| Almacenamiento | HMAC con clave del servidor (un hash simple de 6 dígitos se rompe por fuerza bruta si la tabla se filtra) |
| Vigencia | 10 minutos **[CONTEXTO, propuesto]** |
| Uso | Un solo uso; pedir uno nuevo invalida los anteriores del correo |
| Intentos de verificación | 5 por código **[CONTEXTO, propuesto]** |
| Solicitudes | Mínimo 60 segundos entre solicitudes y máximo 5 por hora por correo, informando cuánto esperar **[CONTEXTO, propuesto]**; además un límite por IP **[TÉC]** para impedir que se use la plataforma para llenar buzones ajenos |
| Respuesta | Idéntica exista o no la cuenta; el rechazo por dominio sí es explícito (el dominio no es secreto) |
| Correo enviado | Solo a dominios permitidos; el texto indica la vigencia y que el código no se comparte con nadie (RNF-11) |

Con 5 solicitudes por hora y 5 intentos por código, un atacante tiene 25 intentos por hora sobre 1.000.000 de combinaciones: cerca de 0,0025 % por hora. El bloqueo temporal tras fallos acumulados se define en el plan de UC01.

#### 7.1.2 Token de sesión (JWT de 24 horas, sin cookies) **[CONTEXTO, RN-54 a RN-58]**

**Emisión.** Al verificar el código, `TokenService` emite un JWT firmado con **HS256**. El **algoritmo lo fija el servidor**: el decodificador solo acepta ese algoritmo y rechaza `alg: none` o cualquier otro (RNF-10). La llave de firma (mínimo 256 bits) vive en una variable de entorno o gestor de secretos, nunca en el repositorio. Cada login genera un token nuevo.

| Claim | Contenido |
|---|---|
| `sub` | Identificador del usuario (`app_user.id`) |
| `email` | Correo institucional |
| `role` | Rol al momento de emitir. Es informativo para la interfaz; **el servidor nunca autoriza con este valor** (RN-58) |
| `iat`, `exp` | Fecha de emisión y de expiración: `exp = iat + 24 h` |
| `reauth_at` | Solo en tokens reautenticados del Administrador (ver abajo) |

**Transporte y almacenamiento.** El token no se guarda en cookies. El cliente lo conserva en el almacenamiento del navegador (`localStorage`, para que lo vean otras pestañas, como la que abre el QR) y lo envía en cada petición con `Authorization: Bearer <token>` (RN-55). El token nunca va en la URL ni en los registros.

**Vigencia.**

| Aspecto | Valor |
|---|---|
| Vigencia | 24 horas desde la emisión, igual para Usuario y Administrador (RN-54) |
| Renovación | Ninguna. Al expirar, la API responde `401 TOKEN_EXPIRED`; el cliente recuerda la pantalla actual, pide iniciar sesión y regresa a ella (RN-56) |
| Inactividad | No se mide: no hay estado en el servidor |
| Dispositivos | Varias sesiones simultáneas; cada login emite un token independiente (RN-57) |

**Cierre de sesión.** El cliente elimina el token de ese dispositivo. `POST /auth/logout` solo registra el cierre en auditoría (RN-61); no revoca el token, porque el servidor no guarda sesiones (RN-57). No existe "cerrar todas las sesiones" (ver limitaciones §7.1.7 y OQ-17).

**Reautenticación del Administrador.** Las acciones que cancelan reservas en masa o cambian las reglas (`POST /admin/blocks`, `PUT /admin/parameters`) exigen un token con `reauth_at` dentro de los últimos 10 minutos **[TÉC]**. Si no lo tiene, responden `403 REAUTH_REQUIRED` y el cliente pide un código nuevo (`/auth/reauth/code`). `POST /auth/reauth/verify` valida el código y devuelve un token nuevo con `reauth_at` y **el mismo `exp`** del token presentado, de modo que no extiende la sesión (RN-56). `ReauthPolicy` (pura) decide si la reautenticación sigue vigente.

#### 7.1.3 Amenazas y mitigaciones

| Amenaza | Mitigación |
|---|---|
| **Robo del token por XSS** (el riesgo principal: `localStorage` es legible por cualquier script de la página) | CSP estricta, escapar toda salida, sin HTML crudo del usuario, sin `eval`, dependencias de terceros mínimas y fijadas (RNF-10); la vigencia de 24 h acota el daño |
| Robo en la red | HTTPS obligatorio y HSTS |
| CSRF | No aplica: el navegador no adjunta el token por sí solo (no hay cookies), por lo que CSRF se desactiva en Spring Security y no se usa token CSRF |
| Fijación de sesión | No aplica; cada login emite un token nuevo |
| Token manipulado o forjado | Firma HS256 verificada en cada petición, algoritmo fijado en el servidor (se rechazan `alg: none` y cambios de algoritmo), validación de `exp` |
| Fuga de la base de datos | Solo se guarda el HMAC de los códigos; no hay tokens ni sesiones en la base de datos, así que no hay nada que permita suplantar |
| Fuga de la llave de firma | Permite forjar tokens: se guarda fuera del código y de la base de datos. Rotarla invalida todos los tokens emitidos (sirve también como corte de emergencia, con un costo: todos vuelven a iniciar sesión) |
| Fuerza bruta del código | 5 intentos, 10 minutos, 60 s y 5 solicitudes por hora (§7.1.1) |
| Llenar buzones ajenos con códigos | Límite por correo y por IP; no se envía a dominios no permitidos |
| Enumeración de cuentas | Misma respuesta exista o no la cuenta |
| Redirección abierta (destino de retorno) | Solo se aceptan rutas internas que empiezan con `/` (no `//`); el cliente valida contra sus rutas conocidas |
| Inyección en cabeceras del correo | El correo se valida estrictamente y nunca se arma una cabecera con texto crudo del cliente |
| Equipo perdido o compartido | Cerrar sesión borra el token de ese navegador; no hay revocación remota (limitación aceptada, OQ-17) |
| Token robado y usado | Vigencia fija de 24 h sin renovación; la suspensión, el cambio de rol o la eliminación del usuario aplican de inmediato por RN-58 |
| Token filtrado en URLs o registros | El token solo viaja en el encabezado; ningún registro lo contiene |
| Phishing del código | No se evita con OTP: el correo advierte que no se comparte. Microsoft con MFA lo reduce (§7.1.7) |
| Correo del Usuario comprometido | Quien controla el buzón controla la cuenta, igual que con SSO; se mitiga con suspensión y con los límites de tiempo |

#### 7.1.4 Autorización: el token no congela los permisos

- `JwtAuthenticationFilter` verifica la firma, el algoritmo y la expiración del token y hace **una sola consulta** a `app_user` por `sub`, para comprobar que el usuario exista y leer su rol y su suspensión actuales.
- El `UserPrincipal(id, email, role)` se arma con los datos **actuales** de `app_user`, no con los claims del token. Si el rol cambia o el usuario se elimina, el efecto es inmediato (RN-58).
- Un Usuario suspendido conserva su token y puede consultar (RN-05); lo que se rechaza es crear reservas, y eso lo valida UC03 contra la base de datos (RN-38, RN-40).
- Los roles se verifican **en el servidor**; ocultar un botón no cuenta como control.
- La configuración de Spring Security es **sin estado**: no crea sesión HTTP, no usa cookies y no activa CSRF.

#### 7.1.5 Ruta de migración a Microsoft (Entra ID)

| Elemento | Hoy (código por correo) | Con Microsoft |
|---|---|---|
| Verificación de la persona | `LoginCodeService` y `/auth/code`, `/auth/verify` | Inicio de sesión con Entra ID (OAuth2/OIDC); el backend valida el ID token |
| Provisionar el usuario | `UserService.resolveOrCreate` | La misma |
| Emisión del token | `TokenService` (JWT propio de 24 h) | La misma, llamada tras validar el token de Microsoft |
| Filtro, autorización, roles, suspensión | `JwtAuthenticationFilter` y consulta a `app_user` | Igual |
| Cambio en endpoints de negocio | — | Ninguno |
| Tablas | `app_user`, `login_code` | Se agrega `external_identity(user_id, provider, subject)` con `UNIQUE(provider, subject)` y se elimina `login_code` |

Notas para la migración:
- Alternativa: validar directamente los tokens de Entra como *resource server* y eliminar `TokenService`. El prototipo mantiene `TokenService` (OQ-15); la validación directa queda como opción para producción. En ambos casos los casos de uso de negocio no cambian.
- El primer ingreso con Microsoft enlaza por correo al `app_user` existente y guarda el `oid`. Desde ahí se identifica por `oid`, porque los correos pueden cambiar.
- Entra no siempre entrega el claim `email`: puede hacer falta `preferred_username` o `upn`. Conviene validar el `tid` del tenant.
- Guardar el `oid` es un dato adicional frente a RNF-09 (solo nombre y correo). Es un identificador técnico, pero debe quedar documentado.
- **Iniciar ya el registro de la aplicación en Entra ID** con TI de la universidad (consentimiento de administrador, URI de redirección). Suele ser lo más lento (OQ-15).
- El token y la autorización se conservan, por eso la migración no toca UC02 a UC12.

#### 7.1.6 Perfil `dev` y pruebas de seguridad

- **Perfil `dev`**: login directo por correo, sin código, que emite el mismo JWT, para avanzar en los demás casos de uso sin depender del correo. Existe solo con `@Profile("dev")`; una prueba verifica que el contexto sin ese perfil no expone `/dev/**`.
- **Mailpit** en `compose.yaml` para ver los correos localmente. Para pruebas reales hace falta un proveedor SMTP con SPF y DKIM: los correos desde cuentas personales hacia `@unimagdalena.edu.co` suelen caer en spam.
- **Pruebas mínimas de UC01**: código inválido, vencido, reutilizado y con 5 intentos; reenvío antes de 60 s y más de 5 solicitudes por hora; dominio no permitido sin envío; misma respuesta exista o no la cuenta; token expirado → `401 TOKEN_EXPIRED`; token con firma alterada, `alg: none` y algoritmo distinto rechazados; token nuevo en cada login; cambio de rol, suspensión o eliminación del usuario efectivos en la siguiente petición sin esperar la expiración (RN-58); respuestas de `/auth/*` sin `Set-Cookie`; destino de retorno externo rechazado; ningún registro contiene códigos ni tokens; `PROFILE_INCOMPLETE`; `REAUTH_REQUIRED` en acciones sensibles y reautenticación que no extiende `exp`.

#### 7.1.7 Limitaciones aceptadas del prototipo

- **Depende del correo**: si el envío falla, cae en spam o tarda, nadie entra.
- **Se puede engañar al Usuario para que entregue su código**; Microsoft con MFA es más robusto.
- **Un correo comprometido compromete la cuenta**, durante hasta 24 horas por cada token emitido.
- **Fricción diaria**: como no hay renovación, el Usuario inicia sesión de nuevo cada 24 horas (RN-56) y debe salir de la app a leer el correo. Con sesión vigente, el login no cuenta en los 5 pasos de la reserva (RNF-01).
- **El token es legible por cualquier script de la página**: un XSS exitoso lo expone. Se mitiga con CSP y escape, pero no se elimina.
- **No hay revocación individual**: un token robado o de un equipo perdido vale hasta que expire (máximo 24 h) o hasta que el usuario sea suspendido (OQ-17).
- **El QR en otro navegador exige un login nuevo** (RN-34).
- **Parte del trabajo es desechable**: `LoginCodeService`, `login_code` y los endpoints `/auth/code`, `/auth/verify` y `/auth/reauth/*` desaparecen con Microsoft.
- **Más trabajo que un login simulado**: Spring Security, filtro JWT, correo y tablas nuevas.

#### 7.1.8 Generales

- El `user_id` de cada reserva, check-in o cancelación sale del token verificado y del estado en la base de datos, nunca de la petición (RN-03, RN-33).
- HTTPS en todo; base de datos sin acceso desde internet; credenciales (incluidas la llave de firma del JWT y la clave del HMAC) en variables de entorno o gestor de secretos, nunca en el repositorio.
- Ningún registro (log o auditoría) contiene códigos de acceso ni tokens.
- Aviso de tratamiento de datos personales (Ley 1581 de 2012); revisar con la universidad si el proyecto se oficializa.

### 7.2 Resiliencia (valores iniciales **[TÉC]** a calibrar)

| Dependencia | Timeout | Reintentos | Comportamiento ante fallo |
|---|---|---|---|
| Servicio de correo (SMTP) | 10 s | 2 con *backoff* | Se registra el fallo; **no se revierte** la reserva, cancelación ni sanción (comportamiento definitivo: OQ-04). Si falla el código de acceso, se informa al Usuario y puede pedir otro |
| Jobs programados | — | Se reintentan en la siguiente ejecución | Idempotentes; un fallo no deja estados a medias |
| Reservas concurrentes | — | — | Gana la primera; la segunda recibe "La franja ya no está disponible" con el calendario actualizado (RN-10) |

### 7.3 Observabilidad
Actuator (`/health`), logs con `reservation_id` y `user_id`. Métricas mínimas: fallos de envío de correo, ejecuciones de jobs y reservas por estado. De autenticación: códigos enviados y fallidos, verificaciones fallidas (un pico indica abuso) y tokens rechazados por expirados o inválidos (un pico de inválidos indica manipulación o una llave mal rotada).

### 7.4 Configuración **[TÉC]**
En `application.properties` (por entorno): zona horaria `America/Bogota`, datos del servidor de correo y los parámetros de seguridad de acceso de la tabla siguiente. Los parámetros de negocio de la sección 4 **no** van aquí: viven en `usage_parameters` y los edita el Administrador (UC06-H2). Los de seguridad de acceso se dejan fuera de `usage_parameters` a propósito (D-20; ver OQ-16). Las claves de autenticación de la tabla llevan el prefijo `app.auth.`; la de CORS conserva `app.cors.`. El dominio de correo permitido sí es un parámetro de negocio y vive en `usage_parameters`.

| Clave | Valor inicial | Nota |
|---|---|---|
| `code.ttl` | 10 minutos | Vigencia del código |
| `code.max-attempts` | 5 | Intentos por código |
| `code.resend-interval` | 60 segundos | Mínimo entre solicitudes del mismo correo |
| `code.max-requests-per-email` | 5 por hora | Solicitudes de código |
| `code.max-requests-per-ip` | por calibrar | Solicitudes de código |
| `code.hmac-secret` | variable de entorno | Nunca en el repositorio |
| `jwt.secret` | variable de entorno | Llave de firma, mínimo 256 bits; nunca en el repositorio |
| `jwt.algorithm` | `HS256` | Fijado en el servidor; no se acepta del cliente |
| `jwt.ttl` | 24 horas | Vigencia del token, sin renovación |
| `reauth.window` | 10 minutos | Vigencia de la reautenticación del Administrador |
| `admin-emails` | lista de correos | Siembra del rol Administrador (OQ-02, RN-60) |
| `app.cors.allowed-origins` | orígenes del frontend React | Lista explícita; se permite el encabezado `Authorization` y el SSE se consume con `fetch` (OQ-13) |

---

## 8. Estados de la reserva

```mermaid
stateDiagram-v2
  [*] --> CONFIRMED
  CONFIRMED --> CANCELLED_ON_TIME: cancela con 60 min o mas
  CONFIRMED --> CANCELLED_LATE: cancela con menos de 60 min
  CONFIRMED --> IN_USE: check-in
  CONFIRMED --> NO_SHOW: ventana cerrada sin check-in
  CONFIRMED --> CANCELLED_BY_ADMIN: bloqueo institucional
  IN_USE --> COMPLETED: termina la franja
```

| Estado | Cuenta contra la cuota | Libera el espacio |
|---|---|---|
| `CONFIRMED` | Sí | No |
| `IN_USE` | Sí | No |
| `COMPLETED` | Sí | — |
| `CANCELLED_ON_TIME` | No | Sí |
| `CANCELLED_LATE` | Sí | Sí |
| `NO_SHOW` | Sí | — |
| `CANCELLED_BY_ADMIN` | No | Sí |

Los cambios de estado los hacen solo los services: `CancellationService` (cancelaciones), `CheckInService` (`IN_USE`), `BlockService` (`CANCELLED_BY_ADMIN`) y `SanctionService`/`ReservationLifecycleJob` (`NO_SHOW`, `COMPLETED`).

---

## 9. Decisiones de diseño y justificación

| ID | Decisión | Alternativas descartadas | Justificación | SPEC |
|---|---|---|---|---|
| D-01 | **Un monolito, un solo despliegue** | Microservicios | Dominio pequeño, una sola base de datos y una operación transaccional crítica (cero cruces) | Todos |
| D-02 | **n capas simples** `controller → service → repository → model` | Hexagonal / Clean Architecture; multi-módulo | Proporcional al tamaño del proyecto; menos ceremonia | Todos |
| D-03 | Módulos de negocio como **subpaquetes dentro de cada capa**; se comunican solo por services | Capas sin orden; un módulo Maven por tema | Mantiene la puerta abierta a separar un módulo sin reglas pesadas | Todos |
| D-04 | **Restricciones de exclusión** en PostgreSQL (espacio + rango y usuario + rango) | Validar solo en el código | Garantiza RN-10 y RN-11 aun con solicitudes simultáneas | UC03 |
| D-05 | **Bloqueo de la fila del espacio** al reservar y al bloquear | Nivel de aislamiento serializable global | La restricción no cubre la tabla de bloqueos; se serializa solo lo necesario | UC03, UC07 |
| D-06 | **Cuota y reinicio derivados** de las reservas, sin tabla de saldo ni job | Tabla de contador semanal con job de reinicio | RN-17: la semana es la de la franja; no hay saldo que desincronizar y el reinicio semanal (UC08-H2) se cumple sin tarea programada | UC03, UC08 |
| D-07 | Jobs `@Scheduled` en el mismo proceso, idempotentes | Broker de mensajes | Un solo sistema, sin infraestructura adicional | UC10, UC11 |
| D-08 | **Login por código de un solo uso al correo institucional**, con token JWT de 24 h sin cookies (RN-49 a RN-58). Microsoft (Entra ID) es el destino final | Contraseña local; Microsoft desde el inicio; enlace mágico; login simulado | Una contraseña propia obliga a registro, verificación y recuperación, y luego hay que descartarla. El código demuestra que la persona controla un buzón del dominio (RN-01), reduce la fricción (RNF-01, RNF-03), se parece al flujo de Microsoft y funciona con el QR en la misma pestaña (RN-34). El login simulado solo existe en el perfil `dev` | UC01 |
| D-09 | Autenticación aislada en el módulo `auth` y `security`; el resto ve un `UserPrincipal`; reservas ligadas a `app_user.id`; `resolveOrCreate` como punto único de aprovisionamiento y `TokenService` como único emisor de tokens | Acoplar reservas o controllers al mecanismo de login | Migrar a Microsoft cambia cómo se verifica la persona y no toca los casos de uso de negocio (§7.1.5) | UC01 |
| D-10 | Comprobante y avisos por **evento posterior al commit** + `MailSender` como puerto | Enviar dentro de la transacción | Un fallo de correo no deshace la reserva; se prueba con un servidor falso | UC11 |
| D-11 | Calendario en vivo con **SSE**, abierto desde el cliente con `fetch` para poder enviar el encabezado `Authorization` | *Polling*; WebSocket; `EventSource` con el token en la URL | RNF-04 con poco código; el `EventSource` nativo no envía encabezados y el token en la URL quedaría en los registros; el *polling* corto es la alternativa si complica | UC02 |
| D-12 | Parámetros en la tabla singleton `usage_parameters` | `application.properties`; tabla clave-valor | El Administrador los edita sin redespliegue (UC06-H2) | UC06 |
| D-13 | **DTO** propios; las entidades JPA no salen del service | Exponer entidades | La API no se acopla al modelo de datos | Todos |
| D-14 | `Clock` inyectado con America/Bogota y `timestamptz` | Hora del servidor; `LocalDateTime` | RNF-07; tolerancia y ventanas se prueban sin depender del reloj real | Todos |
| D-15 | Código en **inglés**, documentación en español (tabla §12) | Todo en español | Coherente con el proyecto anterior **[TÉC]** | Todos |
| D-16 | **13 SPECs con historias de usuario**: los casos que comparten actor, datos y reglas son historias de una misma SPEC | Una SPEC por cada caso del diagrama (11) | Menos archivos y menos contenido repetido; el diagrama sigue siendo la referencia de los casos | Todos |
| D-17 | **Token JWT firmado (HS256) de 24 h, sin cookies y sin tabla de sesiones**: el cliente lo guarda en `localStorage` y lo envía como `Bearer`; algoritmo y llave fijados en el servidor | Sesión en base de datos con cookie opaca (propuesta anterior); Spring Session JDBC; JWT con *refresh token* | Lo define el contexto (RN-54, RN-55, RN-56). No hay estado de sesión que guardar ni limpiar, y no hay CSRF. Costos aceptados: no hay revocación individual (OQ-17) y el token queda expuesto a XSS, mitigado con CSP y escape (RNF-10) | UC01 |
| D-18 | **Una sola vigencia de 24 horas, sin renovación**, igual para todos los roles | Deslizamiento sin tope; perfiles por rol; "Recordarme" | Es lo que define el contexto (RN-54, RN-56). El mayor privilegio del Administrador se compensa con la reautenticación en acciones sensibles (D-21) y con la lectura del estado en cada petición (D-19) | UC01 |
| D-19 | **Existencia, rol y suspensión leídos de la base de datos en cada petición**; el `role` del token es solo informativo | Autorizar con los claims del token | Un cambio de rol, una suspensión o la eliminación del usuario surten efecto de inmediato, sin esperar a que el token expire (RN-58) | UC01, UC03 |
| D-20 | **Parámetros de seguridad de acceso en `application.properties`**, no en `usage_parameters` | Hacerlos editables por el Administrador | Quien configura las reglas de uso no debería poder debilitar la seguridad (alargar el token o quitar el límite de intentos). El contexto los lista en la sección 4; se mantienen fuera de `usage_parameters` (OQ-16) | UC01, UC06 |
| D-21 | **Reautenticación con código nuevo** para las acciones sensibles del Administrador, mediante un token nuevo con `reauth_at` y el mismo `exp` | Confiar en el token de 24 horas; extender la sesión al reautenticar | Los bloqueos cancelan reservas en masa (UC07-H2) y las reglas afectan a todos (UC06-H2); un token abierto de 24 h no basta. Conservar `exp` respeta la ausencia de renovación (RN-56) | UC01, UC06, UC07 |
| D-22 | **Catálogo de implementos con existencias + cantidades por reserva** en `equipment` y `reservation_equipment` | Texto libre en la reserva; catálogo sin stock | Permite elegir implementos conocidos (balones, mallas, redes) y validar la disponibilidad real de cada franja; el Administrador define las existencias (RN-62 a RN-64) | UC13 |
| D-23 | **Disponibilidad de implementos derivada de las reservas**, sin saldo por franja | Tabla de saldos o contadores de inventario | Como las reservas tienen rango de tiempo, el stock se consume solo mientras se solapan; derivarlo evita desincronización y sigue el mismo principio de la cuota (D-06). Las canceladas liberan el stock (RN-65, RN-66) | UC13 |
| D-24 | **Mapeo DTO ↔ entidad con MapStruct** | Mapeo manual; otra librería de mapeo | Evita mapeo repetitivo y errores; el código generado se valida en compilación y no añade reflexión en tiempo de ejecución (decisión del plan, 2026-11-10) | Todos |
| D-25 | **Cliente web en React en un repositorio aparte**; la API se consume desde otro origen con CORS y `Authorization` | Servir el cliente desde el mismo backend; otro framework | Separa la interfaz del backend y permite desplegarlos por separado; el backend queda como API pura (OQ-01, OQ-13) | Todos |

---

## 10. Puntos abiertos resueltos (OQ)

**Estado: todos cerrados (2026-11-10).** La columna «Decisión» registra el cierre.

| ID | Punto | Afecta | Decisión |
|---|---|---|---|
| OQ-01 | Stack, versiones, paquete raíz (el repositorio ya usa `unimagdeportes`, Java 25 y Spring Boot 4.1.1), idioma de los identificadores y tecnología del cliente web | Todo | **[Cerrado 2026-11-10]** Java + Spring Boot + PostgreSQL; paquete `unimagdeportes`; identificadores en inglés; cliente web en **React**, en un repositorio aparte |
| OQ-02 | Cómo se identifica a un Administrador (RN-60 dice que se asigna a correos específicos desde la configuración, sin precisar dónde) | UC01, UC05-H2, UC06-H2, UC07, UC09 | **[Cerrado 2026-11-10]** Columna `role` en `app_user`; el rol se siembra desde `app.auth.admin-emails` que aplica `resolveOrCreate`. Con Microsoft podría pasar a un grupo de Entra |
| OQ-03 | **[Cerrado 2026-10-18]** El login por código y el token JWT de 24 h sin cookies ya están en el contexto (RF-01, RF-22, RN-49 a RN-61) y la SPEC 001 se actualizó | UC01 | Mantener el login por código y documentarlo como sustituible por Microsoft |
| OQ-04 | Comportamiento si falla el envío de correo (comprobante, avisos, código de acceso) | UC01, UC11 | **[Cerrado 2026-11-10]** Registrar el fallo y no revertir la operación; en el código de acceso, informar y permitir reintentar |
| OQ-05 | Efecto de cambiar parámetros o de poner un espacio en mantenimiento sobre reservas existentes; y diferencia entre el estado "en mantenimiento" del espacio y el bloqueo institucional de tipo mantenimiento | UC06-H2, UC07 | **[Cerrado 2026-11-10]** Rige para operaciones nuevas; poner un espacio en mantenimiento no cancela reservas existentes |
| OQ-06 | Estructura del horario de operación y de la recurrencia de los bloqueos | UC07 | **[Cerrado 2026-11-10]** `opening_time`/`closing_time` simples; los bloqueos recurrentes se **materializan** en una fila por franja. El grupo no se reutiliza para editar en el prototipo: para cambiarlos se crea un bloqueo nuevo |
| OQ-07 | Conteo de inasistencias después de una suspensión | UC10 | **[Cerrado 2026-11-10]** Ventana rodante de 30 días; una suspensión reinicia el contador de inasistencias |
| OQ-08 | Período de los indicadores | UC09-H2 | **[Cerrado 2026-11-10]** Períodos fijos semanal y mensual; el frontend permite elegir cuál ver |
| OQ-09 | Horizonte del calendario frente al límite de anticipación para reservar | UC02 | **[Cerrado 2026-11-10]** Horizonte de 7 días, alineado con la anticipación máxima |
| OQ-10 | Si el canal de contacto de RN-48 es el formulario de UC12 | UC11, UC12 | **[Cerrado 2026-11-10]** Sí; el canal es el formulario de UC12 |
| OQ-11 | Dominio exacto de correo (el contexto dice "confirmar") | UC01, UC06 | **[Cerrado 2026-11-10]** `@unimagdalena.edu.co` como parámetro |
| OQ-12 | Contradicciones entre documentos: (a) RN-16 y el Escenario A usaban 3 h; la tabla de parámetros usa 4 h (se tomó 4 h); (b) anticipación máxima: el contexto (RN-14 y sección 4) decía **10 días**, pero la SPEC 003 y este plan usan **7 días**; (c) "desactivar" espacios (RF-11) frente a los estados "activo"/"en mantenimiento" (RN-06) | UC03, UC06, UC07, UC08 | **[Cerrado 2026-11-10]** Cuota de 4 h; anticipación máxima de 7 días; "desactivar" un espacio equivale a pasarlo a mantenimiento; el contexto quedó alineado |
| OQ-13 | Arquitectura del cliente: al no haber cookies, no se exige que cliente y API estén en el mismo sitio, pero un cliente en otro origen requiere CORS con el encabezado `Authorization` y una lista de orígenes permitidos. El SSE no puede usar `EventSource` (ver §5.3) | UC01, UC02, D-17 | **[Cerrado 2026-11-10]** Frontend en otro repositorio: CORS con orígenes explícitos y encabezado `Authorization`; SSE consumido con `fetch` (§5.3) |
| OQ-14 | Dejar ver el calendario sin sesión: reduce fricción, pero UC02 hoy asume usuario autenticado | UC02 | **[Cerrado 2026-11-10]** No; el calendario exige sesión |
| OQ-15 | Registro de la aplicación en Entra ID: consentimiento de administrador, URI de redirección, tenant y claims disponibles (`email`, `preferred_username`, `upn`, `tid`, `oid`); y si el backend sigue emitiendo su propio JWT o valida directamente los tokens de Entra. Guardar `oid` toca RNF-09 | UC01 | **[Cerrado 2026-11-10]** El prototipo mantiene el login por código y el JWT propio; Entra ID queda para producción, con el trámite de TI en paralelo |
| OQ-16 | El contexto lista los parámetros de autenticación (código, intentos, reenvío, vigencia del token) en su sección 4 como configurables por el Administrador; este plan los deja en `application.properties` (D-20) | UC01, UC06-H2 | **[Cerrado 2026-11-10]** Mantenerlos fuera de `usage_parameters` (D-20) |
| OQ-17 | Revocación de tokens: sin sesión en el servidor no se puede cerrar la sesión de un dispositivo perdido antes de que el token expire. Opciones: aceptarlo (24 h máximo), rotar la llave de firma en emergencia, o agregar una marca por usuario (por ejemplo `tokens_valid_after` en `app_user`) que el filtro compare con `iat` | UC01 | **[Cerrado 2026-11-10]** Aceptar la limitación en el prototipo |
| OQ-18 | Límite de cantidad por implemento en una reserva | UC13 | **[Cerrado 2026-11-10]** Sin parámetro adicional: la cantidad queda acotada por el stock disponible en la franja (RN-65) |
| OQ-19 | Conservación del registro de implementos al cancelar la reserva | UC13 | **[Cerrado 2026-11-10]** Se conserva como historial; deja de contar para la disponibilidad (RN-66) |
| OQ-20 | Inclusión de los implementos en el comprobante PDF | UC11, UC13 | **[Cerrado 2026-11-10]** Sí, el comprobante lista los implementos solicitados |
| OQ-21 | Restricción de implementos por disciplina | UC13 | **[Cerrado 2026-11-10]** No; la disciplina es solo una sugerencia para filtrar en la interfaz |

---

## 11. Dependencias y orden de ejecución

### Dependencias entre fases
- **Setup (1)** → sin dependencias. Incluye `compose.yaml` con PostgreSQL y Mailpit. **Foundational (2)** → depende de 1 y **bloquea** todo lo demás.
- **Foundational (2)** se construye en este orden: `MailSender` base → UC01 (código, emisión y verificación del JWT, filtro, `/me`, reautenticación) → correo del código (UC11-H3) → parámetros base → auditoría. El perfil `dev` permite avanzar en las fases siguientes sin depender del correo real.
- **UC06 y UC07-H1 (3)** → dependen de 2 (parámetros y espacios son insumo de todo lo que sigue). UC05-H2 (QR) se agrega aquí porque solo necesita los espacios.
- **UC02 (4)** → depende de 3. **UC03, UC11-H1, UC08 y UC04-H1 (5)** → dependen de 4 (UC03 incluye UC02) y de 3.
- **UC04-H2 y UC05-H1 (6)** → dependen de 5 (el check-in también de UC05-H2).
- **UC07-H2, UC10 y UC11-H2 (7)** → UC07-H2 depende de 5; UC10 de 6; UC11-H2 se construye de forma **incremental**: el servicio base y `MailSender` en la fase 2, y cada plantilla (recordatorio, cancelación, bloqueo, inasistencia, suspensión) junto al caso de uso que la dispara.
- **UC12 y UC09 (8)** → pueden iniciar después de 5 y completarse al final. **UC13 (9)** → depende de UC03 (fase 5) y de UC07-H1 (fase 3); puede adelantarse en paralelo con las fases 6 a 8. **Polish (N)** → después de las fases deseadas.
- **En paralelo desde la fase 1**: el trámite de Entra ID con TI (OQ-15), porque es lo más lento y no depende del código.

```text
1 Setup → 2 Foundational (UC01 login por código + JWT + parámetros base + correo base + auditoría)
        → 3 UC06 + UC07-H1 + UC05-H2 → 4 UC02 → 5 UC03/UC11-H1/UC08/UC04-H1 → 6 UC04-H2/UC05-H1
                                                          │
                                                          └──► 7 UC07-H2/UC10/UC11-H2 ──► 8 UC12/UC09 ──► 9 UC13 ──► N Polish
```

### Qué debe contener cada plan específico por SPEC (`docs/features/NNN-…/plan.md`)
1. Resumen y trazabilidad a los RF, RNF y CE del SPEC (indicando a qué historia pertenece cada uno).
2. Clases concretas (controller, service, policy, repository, model) con rutas reales.
3. Migración SQL exacta.
4. Contrato REST del caso de uso (solicitud, respuesta y errores).
5. Reglas de negocio con ejemplos numéricos (con la cuota de 4 h) y manejo de errores por escenario.
6. Estrategia de pruebas con una prueba por cada criterio de éxito (`CE-xxx`). El plan de UC01 incluye además las pruebas de seguridad de §7.1.6.
7. Tareas granulares con dependencias y los OQ que lo afecten.

---

## 12. Lenguaje ubicuo (contexto → código)

| Término del contexto | Identificador en código |
|---|---|
| Usuario / Administrador | `AppUser` / rol `ADMIN` |
| Persona autenticada en la petición | `UserPrincipal` |
| Código de acceso | `LoginCode` |
| Token de sesión (JWT) | `TokenService` (emisión) / `JwtAuthenticationFilter` (verificación) |
| Reautenticación | `reauth` (claim `reauth_at`) |
| Espacio / Disciplina | `Space` / `Discipline` |
| Franja | `Slot` (derivada, no persistida) |
| Reserva | `Reservation` |
| Implemento | `Equipment` |
| Implemento solicitado en una reserva | `ReservationEquipment` |
| Cuota semanal | `WeeklyQuota` (derivada) |
| Tolerancia | `toleranceMinutes` (parámetro) |
| Check-in | `CheckIn` (`checked_in_at`) |
| Cancelación a tiempo / tardía | `CANCELLED_ON_TIME` / `CANCELLED_LATE` |
| Inasistencia | `NO_SHOW` |
| Bloqueo institucional | `InstitutionalBlock` |
| Parámetros de uso | `UsageParameters` |
| Comprobante | `Receipt` |
| Servicio de correo | `MailSender` |

---

## Notes

- `[UCxx]` mapea cada tarea a su SPEC; `[UCxx-Hy]` a una historia concreta.
- Cada fase debe poder validarse de forma independiente antes de pasar a la siguiente.
- Todos los OQ están cerrados (2026-11-10); las decisiones de §10 son las vigentes. Un punto nuevo se registra como OQ-22 o siguiente.
- **SPEC 001 y SPEC 005 actualizadas el 2026-10-18** para el token JWT de 24 horas sin cookies (código de acceso, límites, primer ingreso, expiración, cierre de sesión y QR en otro navegador).
- **SPEC 013 creada el 2026-11-10** para la solicitud de implementos con stock, y sus pendientes se cerraron con OQ-18 a OQ-21.
- **2026-11-10**: cierre de los OQ propagado al contexto y a las SPEC 001–004 y 006–013 (4 h y 7 días, parámetros de autenticación, comprobante con implementos, períodos de indicadores, inasistencias, canal de contacto, horizonte y sesión, stock de implementos).
- Evitar: tareas vagas, llamadas a repositories de otro módulo y lógica de negocio en controllers.
- Los valores de los ejemplos en los planes específicos son ilustrativos.