# Implementation Plan: UC04 - Gestionar mis reservas

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC04 reúne la consulta de las reservas propias (próximas y pasadas, con estado) y su cancelación desde "Mis reservas", con vista previa del efecto en la cuota y distinción entre cancelación a tiempo y tardía [SPEC 004 RF-001..RF-010]. Los implementos solicitados forman parte del detalle y se liberan al cancelar.

Enfoque técnico: `ReservationQueryService` para la lectura y `CancellationService` + `CancellationPolicy` para la cancelación; ambos reutilizan el modelo de UC03 y publican el evento de correo de UC11 [PLAN D-10].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002, RF-003 | `ReservationQueryService`, repositorio | T006, T007 |
| RF-004 | `ReservationController` (acción de cancelar) | T017 |
| RF-005, RF-006 | `CancellationPolicy`, `CancellationService` | T004, T010 |
| RF-007 | estado `CANCELLED_LATE` | T012 |
| RF-008 | vista previa | T011 |
| RF-009 | bloqueo con check-in | T013 |
| RF-010 | evento de notificación | T015 |
| RNF-001..RNF-003 | DTO, zona horaria, auditoría | T002, T016 |
| CE-001..CE-006 | aceptación y liberación de stock | T014, T018, T019 |
| HU1, HU2 | consulta y cancelación | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; `reservation` y `reservation_equipment` (detalle).
**Messaging**: evento de cancelación después del commit [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: listar y previsualizar sin demora perceptible.
**Constraints**: solo reservas propias (RN-03); no cancelar con check-in (RN-24); umbral de 60 min (RN-21, RN-22); aviso por correo (RN-26); America/Bogota (RNF-07).
**Scale/Scope**: 3 endpoints, sin migraciones, 10 RF + 3 RNF + 6 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/004-gestionar-mis-reservas/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/reservations/   # 3 contratos de este UC
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/reservation/ReservationController.java
├── service/reservation/{ReservationQueryService,CancellationService}.java
├── service/policy/CancellationPolicy.java
├── service/equipment/EquipmentService.java   # forReservation: implementos de una reserva
├── repository/reservation/ReservationRepository.java
├── dto/reservation/ · mapper/reservation/ReservationMapper.java
└── event/{ReservationNotificationEvent,AvailabilityChangedEvent}.java   # reutilizados

src/test/java/unimagdeportes/{contract/reservation,integration/reservation,service/policy,service/reservation}/
```

**Structure Decision**: se reutilizan controller y repositorio de UC03; la cancelación se aísla en su service y política.

### Decisiones de implementación

- **cancelledAt**: es la hora del `Clock` en el momento de cancelar; se devuelve en la respuesta, pero no se persiste en una columna (sin migraciones). La huella persistida es el registro `RESERVATION_CANCELLED` de `audit_log`.
- **Evento de correo**: se reutiliza `ReservationNotificationEvent` con `Type.CANCELLATION` en lugar de un `ReservationCancelledEvent` propio [D-10].
- **Disponibilidad**: la cancelación publica `AvailabilityChangedEvent` [PLAN §5.3].
- **Mapper**: `ReservationMapper` es un `@Component` manual, como `CheckInMapper`, y no una interfaz MapStruct: combina reserva, nombre del espacio e implementos y convierte a America/Bogota. Es una excepción a D-24.
- **Stock**: se libera de forma implícita al cambiar el estado, porque `EquipmentService` solo cuenta las reservas en estados activos; las filas de `reservation_equipment` se conservan como historial.
- **Orden del listado**: `GET /reservations/mine` ordena por `startAt` descendente.

## Reglas de negocio

1. **Privacidad** [RN-03]: solo se listan y cancelan reservas del usuario del token.
2. **Consulta** [RN-05]: un suspendido puede consultar sus reservas.
3. **Cancelación** [RN-20]: en cualquier momento, sin autorización del administrador.
4. **A tiempo** [RN-21]: 60 min o más; libera el espacio y devuelve horas.
5. **Tardía** [RN-22]: menos de 60 min o franja iniciada sin check-in; libera el espacio pero mantiene horas.
6. **Check-in** [RN-24]: una reserva con check-in no se cancela.
7. **Aviso previo** [RN-25]: se muestra el efecto en la cuota antes de confirmar.
8. **Notificación** [RN-26]: cada cancelación genera correo.
9. **Implementos** [RN-66, OQ-19]: se conservan como historial y liberan stock.

## Contrato REST

Fuente: [`contracts/reservations/`](../../techincal-plan/contracts/README.md) (3 archivos: listado, vista previa y cancelación). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: listado, vista previa y cancelación en T018.
- Integración: privacidad, estados, cancelación a tiempo/tardía y check-in en T009/T019.
- Unitario: `CancellationPolicy` en T020; `CancellationService` (bloqueo previo a la lectura y eventos publicados).
- Aceptación: CE-001..CE-006, incluido historial y liberación de stock [SPEC 004].

## Implementation Phases

### Phase 1: Setup

- [x] **T001** Verificar UC01 y UC03; usar la base de eventos y notificaciones de UC11-H2 creada junto con UC01, sin exigir las plantillas completas.

### Phase 2: Foundational

- [x] **T002** Crear los DTO de `dto/reservation/`.
- [x] **T003** Crear `ReservationMapper` para listar implementos.
- [x] **T004** Implementar `CancellationPolicy`.
- [x] **T005** Agregar la consulta de reservas por usuario al repositorio.

### Phase 3: User Stories

- [x] **T006** [US1/P1] Implementar `ReservationQueryService`.
- [x] **T007** [US1/P1] Implementar `GET /reservations/mine`.
- [x] **T008** [US1/P1] Prueba de contrato del listado.
- [x] **T009** [US1/P1] Integración de privacidad y estados.
- [x] **T010** [US2/P1] Implementar `CancellationService`.
- [x] **T011** [US2/P1] Implementar la vista previa con efecto en la cuota.
- [x] **T012** [US2/P1] Implementar la cancelación (`CANCELLED_ON_TIME` / `CANCELLED_LATE`).
- [x] **T013** [US2/P1] Impedir cancelar con check-in (`RESERVATION_NOT_CANCELLABLE`).
- [x] **T014** [US2/P1] Conservar implementos y liberar stock.
- [x] **T015** [US2/P1] Publicar `ReservationNotificationEvent` (`CANCELLATION`) y `AvailabilityChangedEvent`.
- [x] **T016** [US2/P1] Registrar auditoría.
- [x] **T017** [US2/P1] Implementar los dos endpoints de cancelación.
- [x] **T018** [US2/P1] Pruebas de contrato de vista previa y cancelación.
- [x] **T019** [US2/P1] Integración de cancelación a tiempo, tardía y con check-in.
- [x] **T020** [US2/P1] Prueba unitaria de `CancellationPolicy`.

### Phase 4: Polish

- [x] **T021** Revisar mensajes y efecto en cuota.
- [x] **T022** Verificar que un fallo de correo no revierte la cancelación y ejecutar `./mvnw clean verify`.

**Avance:** los tres endpoints están implementados. `ReservationMineIntegrationTest` cubre la privacidad, los ocho estados de `ReservationStatus` y el usuario suspendido. `ReservationCancellationIntegrationTest` cubre:

- cancelación a tiempo, tardía y con la franja ya iniciada, verificando las horas con `QuotaService`;
- liberación del espacio y del stock, con historial de implementos;
- rechazo con check-in o con la reserva ya cancelada;
- vista previa sin efectos;
- fallo de correo sin reversión.

`./mvnw clean verify` terminó con 169 pruebas sin fallos. RNF-001 y CE-005 (mostrar la vista previa antes de confirmar) dependen del cliente React externo.

## Dependencies & Execution Order

`T001 → T002..T005 → T006..T017 → T018..T020 → T021/T022`.

UC04 depende de UC01, UC03 y la base de UC11-H2. Toda cancelación adquiere primero el bloqueo asesor global de §4.1 del plan general y relee la reserva antes de decidir. US1 y US2 pueden probarse por separado.

## Notes

- `[US1]` y `[US2]` permiten implementar consulta y cancelación por separado.
- La cancelación tardía libera el espacio pero mantiene las horas (RN-22).
- Los implementos cancelados se conservan como historial y liberan stock (OQ-19).
- Pendiente de confirmar: hoy se puede cancelar una reserva `CONFIRMED` cuya franja ya terminó, y queda como `CANCELLED_LATE`; según §8 del plan general debería pasar a `NO_SHOW` cuando exista UC10.
- Pendiente de confirmar: la auditoría registra `RESERVATION_CANCELLED` sin distinguir entre a tiempo y tardía; el tipo se deduce del estado de la reserva.

## Checklist de auto-revisión

- [x] Alineado con SPEC 004 y `contracts/reservations/`.
- [x] RF/RNF/CE/HU trazados.
- [x] Privacidad y bloqueo con check-in cubiertos.
- [x] Notificación posterior al commit.
