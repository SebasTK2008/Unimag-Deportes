# Implementation Plan: UC12 - Contactar administrador

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC12 implementa el formulario que envía un mensaje al correo del área de deportes [SPEC 012 RF-001..RF-003]. El mensaje no se persiste; se entrega por correo y este formulario es el canal de contacto que enlazan los correos de la plataforma [OQ-10].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001 | `ContactController` + formulario del cliente React | T004, T011 |
| RF-002 | `ContactService` + `MailSender` | T003, T005 |
| RF-003 | canal de contacto de RN-48 | T006, T011 |
| RNF-001 | formulario móvil y accesible del cliente React | T011 |
| CE-001, CE-002 | pruebas backend y aceptación con cliente/proveedor reales | T006, T007, T008, T011 |
| HU1 | envío de mensaje | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Security, Mail.
**Storage**: N/A; el mensaje no se persiste.
**Messaging**: correo saliente por `MailSender` [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Mailpit/Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: envío inmediato.
**Constraints**: autenticado; sin persistencia; canal de contacto de RN-48 (OQ-10); America/Bogota.
**Scale/Scope**: 1 endpoint, sin migraciones, 3 RF + 1 RNF + 2 CE + 1 HU.

## Project Structure

### Documentation

```text
docs/features/012-contactar-administrador/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/contact/POST-contact.md
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/contact/ContactController.java
├── service/contact/ContactService.java
├── dto/contact/
└── mail/MailSender.java

src/test/java/unimagdeportes/{contract/contact,integration/contact}/
src/test/java/unimagdeportes/service/contact/ContactServiceTest.java
```

**Structure Decision**: módulo mínimo que reutiliza `MailSender` de UC11; no crea tablas.

## Reglas de negocio

1. **Envío** [RF-10]: el mensaje va al correo del área de deportes.
2. **Sin persistencia** [SPEC 012]: no se guarda en base de datos.
3. **Canal de contacto** [RN-48, OQ-10]: los correos de la plataforma enlazan a este formulario.
4. **Validación**: asunto y mensaje obligatorios.
5. **Sesión**: requiere usuario autenticado.

## Contrato REST

Fuente: [`contracts/contact/POST-contact.md`](../../techincal-plan/contracts/contact/POST-contact.md). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato `POST /contact` en T007.
- Integración de envío y validación en T008.
- Configuración y fallos de correo en `ContactServiceTest` y `ContactConfigurationContractTest`.
- Recorrido HTTP → servicio → SMTP real en `ContactSmtpIntegrationTest`, con Mailpit aislado y destinatario ficticio configurado mediante `APP_MAIL_CONTACT_RECIPIENT`; no envía a la cuenta institucional.
- Aceptación: CE-001 y CE-002 [SPEC 012].

## Implementation Phases

### Phase 1: Setup

- [x] **T001** Verificar prerrequisitos backend: autenticación de UC01 y adaptador SMTP de UC11.

### Phase 2: Foundational

- [x] **T002** Crear el DTO de `dto/contact/`.
- [x] **T003** Implementar `ContactService`.

### Phase 3: User Stories

- [x] **T004** [US1/P2] Implementar `POST /contact`.
- [x] **T005** [US1/P2] Enviar al correo del área de deportes.
- [ ] **T006** [US1/P2] Incluir el enlace a este formulario como canal de contacto.
- [x] **T007** [US1/P2] Prueba de contrato.
- [x] **T008** [US1/P2] Integración backend de envío y validación, incluido SMTP con Mailpit.

### Phase 4: Polish

- [x] **T009** Verificar que el mensaje no se persiste.
- [x] **T010** Revisar manejo de fallos de correo y ejecutar `./mvnw clean verify`.
- [ ] **T011** Aceptar el formulario del cliente React: campos y estados de envío, acceso desde los correos conservando el destino tras autenticación/perfil, uso móvil y WCAG; comprobar recepción institucional con el proveedor real.

**Verificación histórica (2026-11-11):** `ContactIntegrationTest` comprobó autenticación, validación, entrega al destinatario configurado y fallo SMTP con un adaptador de prueba; el servicio no persiste el mensaje. `./mvnw clean verify` pasó con 72 pruebas. Esta ejecución no acredita los cambios posteriores.

**Avance verificado (2026-11-16):** las 14 pruebas de `ContactServiceTest`, `ContactContractTest`, `ContactConfigurationContractTest`, `ContactIntegrationTest` y `ContactSmtpIntegrationTest` pasaron sin fallos, errores ni omisiones. Cubren configuración ausente/inválida, campos obligatorios, JSON mal formado, inyección de cabeceras, acceso de Usuario/Administrador, perfil incompleto, token vencido, fallo SMTP y entrega única al destinatario configurado con asunto y contenido correctos. El recorrido HTTP → SMTP se verificó con Mailpit, no con el proveedor institucional. La prueba de integración comprueba además que el envío no añade auditoría; la ausencia de persistencia se sustenta también en la inspección del servicio, sin repositorios ni escrituras en BD.

**Verificación completa (2026-11-16):** `.\mvnw.cmd clean verify` terminó con `BUILD SUCCESS`: 226 pruebas, sin fallos, errores ni omisiones; JAR generado. Se ejecutó sin importar `.env` ni heredar variables `APP_*`, `MAIL_*` o `SPRING_*`. T006 y T011 continúan pendientes de aceptación externa.

## Dependencies & Execution Order

`T001 → T002/T003 → T004..T008 → T009/T010 → T011`.

UC12 depende de UC01 y UC11.

## Notes

- `[US1]` etiqueta la única historia de la SPEC 012.
- No hay migración ni entidad.
- El formulario es el canal de contacto de RN-48 (OQ-10).
- **Destinatario confirmado:** `salcendra@unimagdalena.edu.co`, mediante `APP_MAIL_CONTACT_RECIPIENT`. La plantilla `.env.example` incluye ese valor; la aplicación no tiene destinatario predeterminado. `APP_AUTH_ADMIN_EMAILS` asigna roles, no configura el destinatario.
- **Pendiente para el despliegue:** inyectar el destinatario y la configuración SMTP del proveedor y comprobar recepción institucional. Confirmar la URL pública del formulario y establecer `APP_MAIL_CONTACT_URL`; las URLs de ejemplo no acreditan CE-002.
- El cliente React vive en otro repositorio. T006 y T011 requieren evidencia de integración y aceptación allí; las pruebas backend no acreditan navegación real, comportamiento móvil ni WCAG. La aceptación integral de UC01/UC11 tampoco se deduce del cierre técnico de sus dependencias para UC12.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 012 y el contrato de contacto.
- [x] RF/RNF/CE/HU trazados.
- [x] Sin persistencia del mensaje.
- [ ] Envío al área de deportes.
