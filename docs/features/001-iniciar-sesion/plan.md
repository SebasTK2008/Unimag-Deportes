# Implementation Plan: UC01 - Iniciar sesión

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC01 es la autenticación sin contraseña: la persona solicita un código de un solo uso a su correo institucional y, al verificarlo, recibe un JWT HS256 de 24 horas que el cliente guarda en el navegador y envía como `Authorization: Bearer` [SPEC 001 RF-001..RF-008]. Incluye primer ingreso con nombre, cierre de sesión, reautenticación del Administrador y filtro sin estado que lee el estado actual del usuario en cada petición [SPEC 001 RF-009..RF-017].

Este plan crea además la infraestructura compartida (estructura, Flyway, correo, auditoría y parámetros base) que consumen los demás UCs; por eso concentra Setup y Foundational [PLAN §3, D-08, D-09].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `LoginCodeService`, `LoginCodePolicy` | T015, T017 |
| RF-003, RF-004, RF-005 | `LoginCodePolicy` (generación, hash, vigencia, intentos, reenvío) | T015, T017 |
| RF-006, RF-007, RF-008 | `TokenService` | T016 |
| RF-009 | `AuthService`, `AuthController` | T019, T022 |
| RF-010 | `JwtAuthenticationFilter` | T013 |
| RF-011, RF-012 | `UserService.resolveOrCreate` + siembra de rol | T018 |
| RF-013, RF-014 | `AuthService`, filtro de perfil incompleto | T019, T023 |
| RF-015 | `AuthService` + retorno a pantalla de origen | T019, T023 |
| RF-016 | `MailSender`, `LoginCodeService` | T010, T017 |
| RF-017 | `AuditService` | T007, T024 |
| RNF-001..RNF-005 | configuración, `SecurityConfig`, manejo de errores | T002, T011, T014 |
| CE-001..CE-007 | aceptación | T025, T026, T027 |
| HU1 | flujo completo | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security sin estado, Mail, Scheduling, Actuator, Flyway; Lombok; MapStruct [D-24].
**Storage**: PostgreSQL; `app_user`, `login_code`, `audit_log`, `usage_parameters` [PLAN §4.1].
**Messaging**: el correo del código se envía dentro de la solicitud (excepción al envío post-commit) [PLAN §5.1].
**Testing**: JUnit Jupiter, AssertJ, Mockito y Testcontainers; SMTP real contra Mailpit en pruebas y en local.
**Target Platform**: contenedores Docker; Docker Compose en desarrollo.
**Project Type**: backend monolítico en n capas [D-02]; cliente React en otro repositorio [D-25].
**Performance Goals**: login sin fricción en el celular (RNF-01); una sola consulta a `app_user` por petición [D-19].
**Constraints**: código de 6 dígitos como HMAC, un uso, 15 min y 5 intentos por defecto, configurables por despliegue; límites de reenvío (RN-51..RN-53); JWT de 24 h sin cookies ni renovación (RN-54..RN-56); estado vigente (RN-58); datos mínimos (RNF-09); auditoría sin secretos (RNF-08); America/Bogota (RNF-07); CSP y escape (RNF-10).
**Scale/Scope**: 7 endpoints, 4 entidades base, esquema en la migración única compartida, 17 RF + 5 RNF + 7 CE + 1 HU [SPEC 001].

## Project Structure

### Documentation

```text
docs/features/001-iniciar-sesion/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/auth/   # 7 contratos
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/auth/AuthController.java
├── service/auth/{AuthService,LoginCodeService,TokenService,UserService}.java
├── service/policy/{LoginCodePolicy,ReauthPolicy}.java
├── repository/{AppUserRepository,LoginCodeRepository,AuditLogRepository,UsageParametersRepository}.java
├── model/{AppUser,LoginCode,AuditLog,Role,UsageParameters}.java
├── dto/auth/ · mapper/auth/AuthMapper.java
├── security/{JwtAuthenticationFilter,UserPrincipal,SecurityConfig}.java
├── mail/{MailSender,SmtpMailSender}.java
├── exception/ · config/ClockConfig.java

src/main/resources/db/migration/
└── V1__shared_schema.sql

src/test/java/unimagdeportes/{contract/auth,integration/auth,unit/policy,security}/
```

**Structure Decision**: módulo `auth` aislado; solo `security` y los services de `auth` conocen el mecanismo de login, el resto ve un `UserPrincipal(id, email, role)` [D-09]. `TokenService` es el único emisor y `UserService.resolveOrCreate` el único aprovisionamiento. Todo usa `Clock` [D-14].

## Reglas de negocio

1. **Dominio** [RN-50]: el servidor valida el correo completo; fuera del dominio no se envía nada.
2. **Código** [RN-51]: 6 dígitos, generación segura, HMAC, 15 minutos por defecto y un uso; el nuevo invalida el anterior.
3. **Límites** [RN-52, RN-53]: 5 intentos; 60 s entre solicitudes y 5 por hora.
4. **Token** [RN-54..RN-56]: HS256, 24 h, sin cookies ni renovación; `401 TOKEN_EXPIRED`.
5. **Estado vigente** [RN-58]: rol y suspensión se leen de la base por petición.
6. **Primer ingreso** [RN-59]: nombre obligatorio; sin él solo `/me`, `PUT /me/profile` y `/auth/*`.
7. **Administrador** [RN-60]: rol sembrado desde `app.auth.admin-emails` (OQ-02).
8. **Auditoría** [RN-61]: solicitudes, login y logout sin códigos ni tokens.

## Contrato REST

Fuente: [`contracts/auth/`](../../techincal-plan/contracts/README.md) (7 archivos). Convenciones y errores: [`contracts/README.md`](../../techincal-plan/contracts/README.md). No se duplica el detalle del contrato.

## Estrategia de testing

- Contrato: códigos, errores y ausencia de `Set-Cookie` de los 7 endpoints (T025).
- Integración: flujo completo, límites, expiración y estado vigente (T026).
- Seguridad: firma alterada, `alg: none`, token en URL y auditoría sin secretos (T027).
- Aceptación: una prueba por CE-001..CE-007 [SPEC 001].

## Implementation Phases

### Phase 1: Setup

- [x] **T001** Verificar la estructura de paquetes y agregar MapStruct 1.6.3 al `pom.xml`, incluidos sus procesadores junto con Lombok; comprobar compatibilidad con Java 25 y ajustar si falla.
- [x] **T002** Configurar `application.yaml` (datasource mediante Compose o variables estándar de Spring Boot, correo, `America/Bogota`, `app.auth.*`, `app.cors.allowed-origins`).
- [x] **T003** Configurar `compose.yaml` con PostgreSQL y Mailpit.
- [x] **T004** Configurar Flyway y la extensión en la migración única compartida.

### Phase 2: Foundational

- [x] **T005** Crear `AppUser` y su repositorio.
- [x] **T006** Crear `LoginCode` y su repositorio.
- [x] **T007** Crear `AuditLog`, su repositorio y `AuditService`.
- [x] **T008** Crear `UsageParameters` y su repositorio (base de UC06).
- [x] **T009** Incorporar las tablas base en la migración única compartida.
- [x] **T010** Implementar `MailSender` y `SmtpMailSender`.
- [x] **T011** Configurar `Clock` y crear `BusinessException` + `GlobalExceptionHandler`.
- [x] **T012** Configurar los mappers MapStruct base.
- [x] **T013** Crear `UserPrincipal` e integrar la autenticación JWT (se reutiliza `BearerTokenAuthenticationFilter` de Spring Security).
- [x] **T014** Crear `SecurityConfig` (sin estado, CORS, CSP).

### Phase 3: User Stories

- [x] **T015** [US1/P1] Implementar `LoginCodePolicy`.
- [x] **T016** [US1/P1] Implementar `TokenService`.
- [x] **T017** [US1/P1] Implementar `LoginCodeService`.
- [x] **T018** [US1/P1] Implementar `UserService.resolveOrCreate` y siembra de rol.
- [x] **T019** [US1/P1] Implementar `AuthService` y `ReauthPolicy` (T019 cubre ambos).
- [x] **T020** [US1/P1] Crear DTO de `dto/auth/`.
- [x] **T021** [US1/P1] Crear `AuthMapper`.
- [x] **T022** [US1/P1] Implementar `AuthController` con los 7 endpoints.
- [ ] **T023** [US1/P1] Integrar `PROFILE_INCOMPLETE` y el retorno a la pantalla de origen.
- [x] **T024** [US1/P1] Integrar auditoría `CODE_REQUESTED`, `LOGIN`, `LOGIN_FAILED`, `LOGOUT`.
- [x] **T025** [US1/P1] Pruebas de contrato en `contract/auth/`.
- [x] **T026** [US1/P1] Pruebas de integración en `integration/auth/`.
- [x] **T027** [US1/P1] Pruebas de seguridad en `security/`.

### Phase 4: Polish

- [x] **T028** Verificar que logs y auditoría no contienen códigos ni tokens.
- [x] **T029** Documentar variables de entorno y ejecutar `./mvnw clean verify`; verificar además el JAR con PostgreSQL y Mailpit de Compose.

**Estado verificado (2026-11-11):** backend de UC01 implementado; `./mvnw clean verify` finaliza con 47 pruebas aprobadas, sin omisiones. El JAR también se verificó con un proyecto aislado de Compose: solicitud `202`, entrega SMTP, verificación `200`, consulta y actualización de perfil `200`, logout `204`. MapStruct 1.6.3 y Lombok compilan con Java 25. Una única auditoría externa detectó problemas de atomicidad, auditoría de rechazos, configuración HMAC y cobertura; se aplicaron correcciones y pruebas de regresión, sin solicitar una segunda auditoría.

**T023 y aceptación del cliente:** `PROFILE_INCOMPLETE`, `TOKEN_EXPIRED` y los contratos necesarios están implementados y probados. El retorno a la pantalla de origen (parte de CE-004), la continuidad del QR/check-in (CE-007) y la experiencia móvil requieren el cliente React de otro repositorio; T023 permanece abierta solo por esa integración. Las restricciones específicas de creación de reservas por suspensión pertenecen a UC03/UC10; UC01 permite el acceso y entrega el estado vigente. HTTPS/HSTS y la entrega con un proveedor SMTP externo se verifican en el despliegue. Los adjuntos de correo pertenecen a UC11.

**Detalles de implementación:** los DTO residen en `dto/auth/AuthDtos.java`; `AuthMapper` usa `MappingConfig`. Se conservan `security/TokenService` y `service/user/UserService` existentes. `UserAuthentication` conserva el usuario leído y el JWT verificado durante la petición; `/me` reutiliza esa lectura. Los bloqueos transaccionales de PostgreSQL serializan solicitudes por correo/IP y el consumo de códigos incluso en el primer ingreso. Los intentos fallidos y solicitudes aceptadas con fallo SMTP se confirman; un fallo técnico en el login o reautenticación revierte el consumo del código y el resto de la operación. Las variables están documentadas en `README.md` § Configuración. SMTP se prueba con Mailpit en lugar de añadir GreenMail.

## Dependencies & Execution Order

`T001 → T002..T004 → T005..T009 → T010..T014 → T015..T024 → T025..T027 → T028/T029`.

UC01 no depende de otros UCs y es prerrequisito de todos [PLAN §11]. Los demás módulos consumen `UserPrincipal`, `MailSender`, `Clock`, `AuditService` y `usage_parameters`.

## Notes

- `[US1]` etiqueta la única historia de la SPEC 001.
- Las claves de firma y HMAC nunca van al repositorio.
- El perfil `dev` existe solo con `@Profile("dev")`.
- Sin lógica de negocio en el controller ni acceso a repositories de otros módulos.

## Checklist de auto-revisión

- [x] Backend alineado con SPEC 001 y `contracts/auth/`; aceptación del cliente pendiente según T023.
- [x] RF/RNF/CE/HU trazados a componente y tarea, con alcance externo identificado.
- [x] Sin secretos en logs y auditoría; token en URL no aceptado como autenticación.
- [x] Migración única compartida aplicada en una base vacía.
- [x] Seguridad sin estado, sin cookies, con CORS/CSP.
