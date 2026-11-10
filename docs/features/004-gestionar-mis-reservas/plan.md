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
| CE-001..CE-005 | aceptación | T018, T019 |
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
**Scale/Scope**: 3 endpoints, sin migraciones, 10 RF + 3 RNF + 5 CE + 2 HU.

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
├── repository/ReservationRepository.java
├── dto/reservation/ · mapper/reservation/ReservationMapper.java
└── event/ReservationCancelledEvent.java

src/test/java/unimagdeportes/{contract/reservation,integration/reservation,unit/policy}/
```

**Structure Decision**: se reutilizan controller y repositorio de UC03; la cancelación se aísla en su service y política.

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
- Unitario: `CancellationPolicy` en T020.
- Aceptación: CE-001..CE-005 [SPEC 004].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01, UC03 y la base de UC11-H2.

### Phase 2: Foundational

- [ ] **T002** Crear los DTO de `dto/reservation/`.
- [ ] **T003** Extender `ReservationMapper` para listar implementos.
- [ ] **T004** Implementar `CancellationPolicy`.
- [ ] **T005** Agregar la consulta de reservas por usuario al repositorio.

### Phase 3: User Stories

- [ ] **T006** [US1/P1] Implementar `ReservationQueryService`.
- [ ] **T007** [US1/P1] Implementar `GET /reservations/mine`.
- [ ] **T008** [US1/P1] Prueba de contrato del listado.
- [ ] **T009** [US1/P1] Integración de privacidad y estados.
- [ ] **T010** [US2/P1] Implementar `CancellationService`.
- [ ] **T011** [US2/P1] Implementar la vista previa con efecto en la cuota.
- [ ] **T012** [US2/P1] Implementar la cancelación (`CANCELLED_ON_TIME` / `CANCELLED_LATE`).
- [ ] **T013** [US2/P1] Impedir cancelar con check-in (`RESERVATION_NOT_CANCELLABLE`).
- [ ] **T014** [US2/P1] Conservar implementos y liberar stock.
- [ ] **T015** [US2/P1] Publicar `ReservationCancelledEvent`.
- [ ] **T016** [US2/P1] Registrar auditoría.
- [ ] **T017** [US2/P1] Implementar los dos endpoints de cancelación.
- [ ] **T018** [US2/P1] Pruebas de contrato de vista previa y cancelación.
- [ ] **T019** [US2/P1] Integración de cancelación a tiempo, tardía y con check-in.
- [ ] **T020** [US2/P1] Prueba unitaria de `CancellationPolicy`.

### Phase 4: Polish

- [ ] **T021** Revisar mensajes y efecto en cuota.
- [ ] **T022** Verificar que un fallo de correo no revierte la cancelación y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001 → T002..T005 → T006..T017 → T018..T020 → T021/T022`.

UC04 depende de UC01, UC03 y la base de UC11-H2. US1 y US2 pueden probarse por separado.

## Notes

- `[US1]` y `[US2]` permiten implementar consulta y cancelación por separado.
- La cancelación tardía libera el espacio pero mantiene las horas (RN-22).
- Los implementos cancelados se conservan como historial y liberan stock (OQ-19).

## Checklist de auto-revisión

- [ ] Alineado con SPEC 004 y `contracts/reservations/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Privacidad y bloqueo con check-in cubiertos.
- [ ] Notificación posterior al commit.
