# Implementation Plan: UC11 - Enviar correos al usuario

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC11 implementa los correos de la plataforma: comprobante PDF de la reserva, notificaciones de eventos y correo con el código de acceso [SPEC 011 RF-001..RF-015]. Comprobante y avisos se envían después del commit y un fallo de correo no revierte la operación; el código de acceso es la excepción y permite reintentar [OQ-04].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002, RF-004 | `ReceiptService`, `ReceiptPdfGenerator` | T005, T006 |
| RF-003, RF-009 | canal de contacto (UC12) | T008, T014 |
| RF-005 | `ReminderJob` | T012 |
| RF-006, RF-007, RF-008 | `NotificationService` | T010 |
| RF-010 | `MailSender` | T003 |
| RF-011..RF-015 | correo del código de acceso | T016, T017 |
| RNF-001..RNF-004 | zona horaria, datos mínimos, entrega, hash | T007, T014 |
| CE-001..CE-008 | aceptación | T009, T015, T018 |
| HU1, HU2, HU3 | comprobante, avisos y código | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Mail, Scheduling, Data JPA. PDF: `TODO: elegir librería` (se decide en implementación). Plantillas de correo.
**Storage**: PostgreSQL; `reservation.reminder_sent_at` para el recordatorio.
**Messaging**: eventos internos después del commit; sin colas [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers, GreenMail; Mailpit en local.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico; llamadas internas y `ReminderJob` [D-07].
**Performance Goals**: correo en menos de 1 minuto en condiciones normales (RNF-11).
**Constraints**: PDF con espacio, fecha, hora, nombre, implementos y reglas (RN-45, OQ-20); canal de contacto (RN-48); recordatorio 2 h antes (RN-46); avisos (RN-47); fallo no revierte (OQ-04); sin datos innecesarios (RNF-09).
**Scale/Scope**: sin endpoints, sin migraciones, 15 RF + 4 RNF + 8 CE + 3 HU.

## Project Structure

### Documentation

```text
docs/features/011-enviar-correos-al-usuario/
├── plan.md
└── spec.md
```

### Source Code

```text
src/main/java/unimagdeportes/
├── mail/{MailSender,SmtpMailSender,ReceiptPdfGenerator}.java
├── mail/templates/
├── service/notification/{ReceiptService,NotificationService}.java
├── scheduler/ReminderJob.java
├── event/{ReservationCreatedEvent,ReservationCancelledEvent,SanctionAppliedEvent,BlockCreatedEvent}.java
├── repository/ReservationRepository.java
└── dto/mail/

src/test/java/unimagdeportes/{integration/mail,unit/mail}/
```

**Structure Decision**: `MailSender` es el puerto y `SmtpMailSender` el adaptador; los servicios de negocio publican eventos y `ReceiptService`/`NotificationService` los consumen después del commit [D-10].

## Reglas de negocio

1. **Comprobante** [RN-45, OQ-20]: PDF con espacio, fecha, hora, nombre, implementos y reglas de cancelación.
2. **Recordatorio** [RN-46]: 2 h antes, con enlace para cancelar.
3. **Avisos** [RN-47]: cancelación, bloqueo, inasistencia y suspensión.
4. **Canal de contacto** [RN-48, OQ-10]: formulario de UC12.
5. **Fallos** [OQ-04]: se registran y no revierten la operación; el código permite reintentar.
6. **Código de acceso** [RN-51, RNF-11]: 6 dígitos, vigencia, no compartir y entrega en menos de 1 minuto.
7. **Datos mínimos** [RNF-09]: solo nombre y correo.
8. **Zona horaria** [RNF-07]: America/Bogota.

## Contrato REST

No aplica: UC11 es interno, sin endpoints. Los correos se disparan desde UC01, UC03, UC04, UC07 y UC10.

## Estrategia de testing

- Integración: comprobante con y sin implementos en T009.
- Integración: recordatorio, cancelación, bloqueo, inasistencia y suspensión en T015.
- Integración: correo del código y dominio inválido en T018.
- Aceptación: CE-001..CE-008 [SPEC 011].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01, UC03, UC04, UC07 y UC10.
- [ ] **T002** Elegir y documentar la librería de PDF.

### Phase 2: Foundational

- [ ] **T003** Reutilizar `MailSender` y `SmtpMailSender` de UC01.
- [ ] **T004** Configurar los escuchas de eventos base.

### Phase 3: User Stories

- [ ] **T005** [US1/P1] Implementar `ReceiptPdfGenerator`.
- [ ] **T006** [US1/P1] Implementar `ReceiptService` y su escucha de `ReservationCreatedEvent`.
- [ ] **T007** [US1/P1] Crear DTO de comprobante.
- [ ] **T008** [US1/P1] Incluir el canal de contacto.
- [ ] **T009** [US1/P1] Integración de comprobante con y sin implementos y verificación de envío post-commit.
- [ ] **T010** [US2/P1] Implementar `NotificationService` y sus escuchas.
- [ ] **T011** [US2/P1] Crear las plantillas de correo.
- [ ] **T012** [US2/P1] Implementar `ReminderJob` (2 h antes, idempotente).
- [ ] **T013** [US2/P1] Incluir el enlace de cancelación en el recordatorio.
- [ ] **T014** [US2/P1] Incluir el canal de contacto y registrar fallos sin revertir.
- [ ] **T015** [US2/P1] Integración de notificaciones por evento.
- [ ] **T016** [US3/P1] Crear la plantilla del código de acceso.
- [ ] **T017** [US3/P1] Integrar el envío desde `LoginCodeService`.
- [ ] **T018** [US3/P1] Integración del correo del código y dominio inválido.

### Phase 4: Polish

- [ ] **T019** Revisar los correos con Mailpit.
- [ ] **T020** Verificar que un fallo de correo no revierte operaciones y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001/T002 → T003/T004 → T005..T009 → T010..T015 → T016..T018 → T019/T020`.

UC11 depende de UC01, UC03, UC04, UC07 y UC10. UC11-H3 (código) se implementa en la fase Foundational de UC01.

## Notes

- `[US1]`, `[US2]` y `[US3]` permiten implementar cada correo por separado.
- El comprobante incluye los implementos (OQ-20).
- No hay endpoint propio.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 011.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Envío después del commit y fallos no reversivos.
- [ ] Comprobante con implementos y canal de contacto.
