# Implementation Plan: UC12 - Contactar administrador

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC12 implementa el formulario que envía un mensaje al correo del área de deportes [SPEC 012 RF-001..RF-003]. El mensaje no se persiste; se entrega por correo y este formulario es el canal de contacto que enlazan los correos de la plataforma [OQ-10].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001 | `ContactController` | T004 |
| RF-002 | `ContactService` + `MailSender` | T003, T005 |
| RF-003 | canal de contacto de RN-48 | T006 |
| RNF-001 | DTO y validación | T002 |
| CE-001, CE-002 | aceptación | T007, T008 |
| HU1 | envío de mensaje | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Security, Mail.
**Storage**: N/A; el mensaje no se persiste.
**Messaging**: correo saliente por `MailSender` [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, GreenMail/Testcontainers.
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
- Aceptación: CE-001 y CE-002 [SPEC 012].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01 y UC11.

### Phase 2: Foundational

- [x] **T002** Crear el DTO de `dto/contact/`.
- [x] **T003** Implementar `ContactService`.

### Phase 3: User Stories

- [x] **T004** [US1/P2] Implementar `POST /contact`.
- [x] **T005** [US1/P2] Enviar al correo del área de deportes.
- [ ] **T006** [US1/P2] Incluir el enlace a este formulario como canal de contacto.
- [ ] **T007** [US1/P2] Prueba de contrato.
- [ ] **T008** [US1/P2] Integración de envío y validación.

### Phase 4: Polish

- [x] **T009** Verificar que el mensaje no se persiste.
- [ ] **T010** Revisar manejo de fallos de correo y ejecutar `./mvnw clean verify`.

**Avance verificado (2026-11-11):** `ContactIntegrationTest` comprueba autenticación, validación, entrega al destinatario configurado y fallo SMTP con un adaptador de prueba; el servicio no persiste el mensaje. `./mvnw clean verify` pasó con 72 pruebas. El formulario del cliente, la ruta y el destinatario reales continúan pendientes; T006–T008 siguen abiertas para su verificación completa.

## Dependencies & Execution Order

`T001 → T002/T003 → T004..T008 → T009/T010`.

UC12 depende de UC01 y UC11.

## Notes

- `[US1]` etiqueta la única historia de la SPEC 012.
- No hay migración ni entidad.
- El formulario es el canal de contacto de RN-48 (OQ-10).
- **Pendiente para el despliegue:** definir y configurar la dirección de correo del área de deportes. La ruta del formulario en el frontend aún no está definida.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 012 y el contrato de contacto.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Sin persistencia del mensaje.
- [ ] Envío al área de deportes.
