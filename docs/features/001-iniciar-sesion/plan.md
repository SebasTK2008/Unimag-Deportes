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
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers, GreenMail; Mailpit en local.
**Target Platform**: contenedores Docker; Docker Compose en desarrollo.
**Project Type**: backend monolítico en n capas [D-02]; cliente React en otro repositorio [D-25].
**Performance Goals**: login sin fricción en el celular (RNF-01); una sola consulta a `app_user` por petición [D-19].
**Constraints**: código de 6 dígitos como HMAC, un uso, 10 min, 5 intentos y límites de reenvío (RN-51..RN-53); JWT de 24 h sin cookies ni renovación (RN-54..RN-56); estado vigente (RN-58); datos mínimos (RNF-09); auditoría sin secretos (RNF-08); America/Bogota (RNF-07); CSP y escape (RNF-10).
**Scale/Scope**: 7 endpoints, 4 entidades base, 5 migraciones, 17 RF + 5 RNF + 7 CE + 1 HU [SPEC 001].

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
├── V1__enable_extensions.sql
├── V2__create_app_user.sql
├── V3__create_login_code.sql
├── V4__create_audit_log.sql
└── V5__create_usage_parameters.sql

src/test/java/unimagdeportes/{contract/auth,integration/auth,unit/policy,security}/
```

**Structure Decision**: módulo `auth` aislado; solo `security` y los services de `auth` conocen el mecanismo de login, el resto ve un `UserPrincipal(id, email, role)` [D-09]. `TokenService` es el único emisor y `UserService.resolveOrCreate` el único aprovisionamiento. Todo usa `Clock` [D-14].

## Reglas de negocio

1. **Dominio** [RN-50]: el servidor valida el correo completo; fuera del dominio no se envía nada.
2. **Código** [RN-51]: 6 dígitos, generación segura, HMAC, 10 minutos, un uso; el nuevo invalida el anterior.
3. **Límites** [RN-52, RN-53]: 5 intentos; 60 s entre solicitudes y 5 por hora.
4. **Token** [RN-54..RN-56]: HS256, 24 h, sin cookies ni renovación; `401 TOKEN_EXPIRED`.
5. **Estado vigente** [RN-58]: rol y suspensión se leen de la base por petición.
6. **Primer ingreso** [RN-59]: nombre obligatorio; sin él solo `/me` y `/auth/*`.
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

- [ ] **T001** Verificar la estructura de paquetes y agregar MapStruct al `pom.xml`.
- [ ] **T002** Configurar `application.properties` (datasource, correo, `America/Bogota`, `app.auth.*`, `app.cors.allowed-origins`).
- [ ] **T003** Configurar `compose.yaml` con PostgreSQL y Mailpit.
- [ ] **T004** Configurar Flyway y `V1__enable_extensions.sql`.

### Phase 2: Foundational

- [ ] **T005** Crear `AppUser` y su repositorio.
- [ ] **T006** Crear `LoginCode` y su repositorio.
- [ ] **T007** Crear `AuditLog`, su repositorio y `AuditService`.
- [ ] **T008** Crear `UsageParameters` y su repositorio (base de UC06).
- [ ] **T009** Crear las migraciones `V2` a `V5`.
- [ ] **T010** Implementar `MailSender` y `SmtpMailSender`.
- [ ] **T011** Configurar `Clock` y crear `BusinessException` + `GlobalExceptionHandler`.
- [ ] **T012** Configurar los mappers MapStruct base.
- [ ] **T013** Crear `UserPrincipal` y `JwtAuthenticationFilter`.
- [ ] **T014** Crear `SecurityConfig` (sin estado, CORS, CSP).

### Phase 3: User Stories

- [ ] **T015** [US1/P1] Implementar `LoginCodePolicy`.
- [ ] **T016** [US1/P1] Implementar `TokenService`.
- [ ] **T017** [US1/P1] Implementar `LoginCodeService`.
- [ ] **T018** [US1/P1] Implementar `UserService.resolveOrCreate` y siembra de rol.
- [ ] **T019** [US1/P1] Implementar `AuthService` y `ReauthPolicy` (T019 cubre ambos).
- [ ] **T020** [US1/P1] Crear DTO de `dto/auth/`.
- [ ] **T021** [US1/P1] Crear `AuthMapper`.
- [ ] **T022** [US1/P1] Implementar `AuthController` con los 7 endpoints.
- [ ] **T023** [US1/P1] Integrar `PROFILE_INCOMPLETE` y el retorno a la pantalla de origen.
- [ ] **T024** [US1/P1] Integrar auditoría `CODE_REQUESTED`, `LOGIN`, `LOGIN_FAILED`, `LOGOUT`.
- [ ] **T025** [US1/P1] Pruebas de contrato en `contract/auth/`.
- [ ] **T026** [US1/P1] Pruebas de integración en `integration/auth/`.
- [ ] **T027** [US1/P1] Pruebas de seguridad en `security/`.

### Phase 4: Polish

- [ ] **T028** Verificar que logs y auditoría no contienen códigos ni tokens.
- [ ] **T029** Documentar variables de entorno y ejecutar `./mvnw clean verify` con Compose.

## Dependencies & Execution Order

`T001 → T002..T004 → T005..T009 → T010..T014 → T015..T024 → T025..T027 → T028/T029`.

UC01 no depende de otros UCs y es prerrequisito de todos [PLAN §11]. Los demás módulos consumen `UserPrincipal`, `MailSender`, `Clock`, `AuditService` y `usage_parameters`.

## Notes

- `[US1]` etiqueta la única historia de la SPEC 001.
- Las claves de firma y HMAC nunca van al repositorio.
- El perfil `dev` existe solo con `@Profile("dev")`.
- Sin lógica de negocio en el controller ni acceso a repositories de otros módulos.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 001 y `contracts/auth/`.
- [ ] RF/RNF/CE/HU trazados a componente y tarea.
- [ ] Sin secretos en logs, auditoría ni URL.
- [ ] Migraciones `V1..V5` en orden.
- [ ] Seguridad sin estado, sin cookies, con CORS/CSP.
