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
**Primary Dependencies**: Spring Boot 4.2.0: Mail, Scheduling, Data JPA. PDF: `com.github.librepdf:openpdf:3.0.5`. Plantillas de correo.
**Storage**: PostgreSQL; `reservation.reminder_sent_at` para el recordatorio.
**Messaging**: eventos internos después del commit; sin colas [D-10].
**Testing**: JUnit 5, AssertJ, Mockito y Testcontainers con PostgreSQL y Mailpit.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico; llamadas internas y `ReminderJob` [D-07].
**Performance Goals**: correo en menos de 1 minuto en condiciones normales (RNF-11).
**Constraints**: plantilla PDF definitiva sin rediseño, con datos de reserva/usuario, implementos y contacto; reglas y check-in solo en el correo (RN-45, OQ-20); recordatorio 2 h antes (RN-46); avisos con consecuencias reales de cancelación (RN-47); fallo no revierte (OQ-04); sin datos innecesarios (RNF-09).
**Scale/Scope**: sin endpoints, sin migraciones, 17 RF + 4 RNF + 8 CE + 3 HU.

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
├── mail/{MailSender,SmtpMailSender,MailBody,MailLayout,ReceiptPdfGenerator,ReceiptMessage,LoginCodeMessage}.java
├── service/notification/{ReceiptService,NotificationService}.java
├── scheduler/ReminderJob.java
├── event/{ReservationCreatedEvent,ReservationNotificationEvent}.java   # Type: REMINDER, CANCELLATION, BLOCK, NO_SHOW, SUSPENSION
├── service/reservation/  # consulta mediante servicio propietario
└── dto/mail/

src/test/java/unimagdeportes/mail/
src/test/java/unimagdeportes/integration/mail/
src/test/java/unimagdeportes/integration/auth/LoginSmtpIntegrationTest.java
src/main/resources/pdf/comprobante-definitivo.pdf
```

**Structure Decision**: `MailSender` es el puerto y `SmtpMailSender` el adaptador; los servicios de negocio publican eventos y `ReceiptService`/`NotificationService` los consumen después del commit [D-10].

## Reglas de negocio

1. **Comprobante** [RN-45, OQ-20]: PDF sobre la plantilla original en `src/main/resources/pdf/comprobante-definitivo.pdf`. Se rellenan los datos; se conservan logos, firma gráfica, textos y dimensiones. El cuerpo de implementos es una única área enumerada; no se limita a tres artículos. El texto añadido se ajusta y las listas que exceden el área continúan sobre otra copia de la misma plantilla. Reglas de cancelación y check-in van solo en el correo.
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

- [ ] **T001** Verificar UC01 y UC03 para el comprobante; crear la base de eventos y notificaciones junto con UC01. Integrar las plantillas y pruebas de UC04, UC07 y UC10 cuando estén disponibles, sin exigirlos para iniciar la base.
- [x] **T002** Agregar OpenPDF y verificar la versión compatible.

### Phase 2: Foundational

- [x] **T003** Reutilizar `MailSender` y `SmtpMailSender` de UC01.
- [x] **T004** Configurar los escuchas de eventos base.

**Base disponible:** `MailSender` y `SmtpMailSender` admiten adjuntos PDF. `SmtpMailSenderTest` verifica la preparación MIME y `SmtpDeliveryTest` la entrega SMTP de un adjunto a Mailpit. Las escuchas consumen eventos internos después del commit; la creación real de reservas ya publica `ReservationCreatedEvent` y la cancelación de UC04 publica `ReservationNotificationEvent` con `Type.CANCELLATION`. `ReservationLifecycleJob` (UC10) publica `NO_SHOW`, `SUSPENSION` y `CANCELLATION` con motivo para cada reserva cancelada por la suspensión. Los bloqueos de UC07 publican `BLOCK`.

Contenido de los avisos, todos en America/Bogota y con el canal de contacto:

- **Cancelación:** fecha/hora, causa conocida y efectos del estado resultante. `CancellationDetails` conserva estado y horas de la reserva en el evento post-commit; no se deduce el efecto del texto libre del motivo ni se vuelve a evaluar con reglas posteriores. A tiempo/suspensión libera horas y tardía las mantiene; bloqueo también libera horas. Se libera espacio e implementos.
- **Inasistencia:** fecha y hora de la reserva, y aviso de que sus horas siguen contando en la cuota (RN-37).
- **Suspensión:** la fecha en que el Usuario podrá reservar de nuevo (RN-39).

**Presentación:** `MailBody` transporta texto plano y HTML; `SmtpMailSender` genera MIME alternativo y mantiene adjuntos/reintentos. Los métodos anteriores de texto plano se conservan para otros envíos. `MailLayout` escapa datos variables y usa tablas/estilos en línea sin recursos remotos; solo enlaza contactos HTTP(S) válidos. `ReceiptMessage` compone la confirmación con reglas y explicación de check-in. `LoginCodeMessage.compose` presenta el código como texto centrado de 40 px, con vigencia y contacto.

**Plantilla verificable:** la copia original tiene SHA-256 `30e5c02c2fa2310b4c722a13f0501041ee412c131ba18d3887378dd32ea2679c`, una página carta de 612 × 792 puntos y no tiene AcroForm. OpenPDF importa la página y escribe los valores en coordenadas fijas; solo cubre el interior del cuerpo de implementos para unificar sus filas. No se requiere el archivo de Descargas en ejecución. La firma preservada es gráfica, no una firma digital criptográfica.

### Phase 3: User Stories

- [x] **T005** [US1/P1] Implementar `ReceiptPdfGenerator`.
- [x] **T006** [US1/P1] Implementar `ReceiptService` y su escucha de `ReservationCreatedEvent`.
- [x] **T007** [US1/P1] Crear DTO de comprobante.
- [x] **T008** [US1/P1] Incluir el canal de contacto.
- [x] **T009** [US1/P1] Integración de comprobante sin implementos y envío post-commit; añadir la prueba con implementos al integrar UC13.
- [x] **T010** [US2/P1] Implementar `NotificationService` y sus escuchas.
- [x] **T011** [US2/P1] Crear las plantillas de correo.
- [x] **T012** [US2/P1] Implementar `ReminderJob` (2 h antes, idempotente).
- [x] **T013** [US2/P1] Incluir el enlace de cancelación en el recordatorio.
- [x] **T014** [US2/P1] Incluir el canal de contacto y registrar fallos sin revertir.
- [x] **T015** [US2/P1] Integración de notificaciones por evento.
- [x] **T016** [US3/P1] Crear la plantilla del código de acceso.
- [x] **T017** [US3/P1] Integrar el envío desde `LoginCodeService`.
- [x] **T018** [US3/P1] Integración del correo del código y dominio inválido.

### Phase 4: Polish

- [ ] **T019** Revisar los correos con Mailpit.
- [x] **T020** Verificar que un fallo de correo no revierte operaciones y ejecutar `./mvnw clean verify`.

**Avance verificado el 24/11/2026:** `clean verify` aprobó 346 pruebas, sin fallos, errores ni omisiones, y generó el JAR. El generador conserva el hash de la plantilla original; se probaron comprobantes sin implementos, con cinco y con 35 repartidos en páginas de continuación, además de la ausencia de reglas/check-in dentro del PDF. La prueba de equipamiento relaciona cada nombre con su cantidad en la misma fila y rechaza cantidades intercambiadas. `EquipmentIntegrationTest` comprueba una creación real con implementos y correo simulado; `LoginSmtpIntegrationTest` recorre solicitud de código por SMTP, login, perfil, creación por `POST /reservations` y entrega del PDF por SMTP a Mailpit, con ambas alternativas de correo. El código usa `APP_MAIL_CONTACT_URL`; si falta, responde `503 MAIL_DELIVERY_FAILED`. La aceptación en buzones y enlaces públicos reales sigue pendiente; Mailpit no envía a cuentas institucionales.

**Avance adicional:** `ReceiptService` y `NotificationService` consumen eventos posteriores al commit; las pruebas controladas cubren rollback, fallo de correo y los cinco tipos de aviso. `ReminderJob` selecciona reservas elegibles y marca el envío después de entregarlo. Su programación usa `app.jobs.reminder-cron` (`APP_JOBS_REMINDER_CRON`), cada minuto por defecto; `-` desactiva la ejecución automática. Las pruebas con reloj controlado desactivan ambos jobs y los invocan explícitamente para evitar interferencias con la limpieza de BD; `JobSchedulingIntegrationTest` comprueba que no quedan tareas programadas. `ReminderFlowIntegrationTest` lo prueba con una reserva creada por `POST /reservations` (UC03): envía el enlace de cancelación y omite la reserva cancelada.

Los cinco avisos se prueban desde sus productores reales:

- cancelación en `ReservationCancellationIntegrationTest` (UC04);
- bloqueo en `BlockIntegrationTest` (UC07);
- inasistencia, suspensión y cancelación por suspensión en `ReservationLifecycleIntegrationTest` (UC10);
- recordatorio en `ReminderFlowIntegrationTest`.

En la cancelación y la inasistencia se verifica además que un fallo de correo no revierte la operación; para el bloqueo lo cubre la prueba controlada de `NotificationEventsTest`. T012–T015 quedan cubiertas. `NotificationEventsTest` también contrasta las consecuencias de cancelación a tiempo, tardía y por suspensión en texto y HTML. Comprobantes/notificaciones mantienen su escucha post-commit y las pruebas de rollback. Si no se configura el contacto no se envían; el recordatorio requiere además `APP_MAIL_CANCELLATION_URL` con `{id}`.

**Límite del recordatorio:** el job serializa sus ejecuciones dentro de una instancia. Conforme a `docs/techincal-plan/plan-general.md` §5.2, el despliegue previsto es de una sola instancia; varias instancias requieren coordinación adicional. Si SMTP acepta el mensaje pero falla el marcado posterior o cae el proceso, un reintento puede duplicarlo. No se garantiza entrega exactamente una vez.

## Dependencies & Execution Order

`T001/T002 → T003/T004 → T005..T009 → T010..T015 → T016..T018 → T019/T020`.

UC11-H3 y la base de correo y eventos se implementan con UC01; UC11-H1 requiere UC03 y se integra con UC13 para implementos. Las plantillas de UC11-H2 se integran incrementalmente con UC04, UC07 y UC10; esas funciones no bloquean la base de UC11-H2.

## Notes

- `[US1]`, `[US2]` y `[US3]` permiten implementar cada correo por separado.
- T016–T018 integran y verifican el correo del código implementado en UC01; no duplican la implementación original.
- El comprobante incluye los implementos (OQ-20).
- No hay endpoint propio.
- **Pendiente para el despliegue e integración del frontend:** definir el correo del área de deportes y las direcciones del formulario de contacto y del flujo de cancelación. Los endpoints documentados pertenecen a la API y no sustituyen las rutas del frontend; verificar los enlaces cuando estén disponibles.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 011.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Envío después del commit y fallos no reversivos.
- [ ] Comprobante con implementos y canal de contacto.
