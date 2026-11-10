# Implementation Plan: UC10 - Aplicar sanciones

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC10 implementa las sanciones automáticas: marcar como inasistencia las reservas sin check-in al cerrar la ventana, mantener sus horas contra la cuota y suspender por 7 días al acumular 3 inasistencias en una ventana rodante de 30 días [SPEC 010 RF-001..RF-006]. Una suspensión reinicia el contador. No hay endpoint: se ejecuta por scheduler.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001 | `SanctionService.markNoShow` | T004 |
| RF-002 | `SanctionService.evaluateSuspension` | T010 |
| RF-003 | evento de notificación (UC11) | T005, T013 |
| RF-004 | reservas futuras y bloqueo de nuevas | T012 |
| RF-005 | parámetros de suspensión (UC06) | T010 |
| RF-006 | ventana rodante y reinicio (OQ-07) | T011 |
| RNF-001..RNF-002 | auditoría y zona horaria | T007 |
| CE-001..CE-003 | aceptación | T008, T014, T015 |
| HU1, HU2 | inasistencia y suspensión | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Data JPA, Scheduling, Mail; MapStruct [D-24].
**Storage**: PostgreSQL; actualización de `reservation.status` y `app_user.suspended_until`.
**Messaging**: evento de sanción después del commit [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers; `Clock` inyectado para simular tiempo.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico; job interno [D-07].
**Performance Goals**: job idempotente cada minuto sobre volumen bajo.
**Constraints**: sin check-in → `NO_SHOW` con horas contadas (RN-37); 3 en 30 días → 7 días (RN-38); ventana rodante y reinicio (OQ-07); reservas futuras se mantienen (RN-40); aviso (RN-39); America/Bogota (RNF-07).
**Scale/Scope**: sin endpoints, sin migraciones, 6 RF + 2 RNF + 3 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/010-aplicar-sanciones/
├── plan.md
└── spec.md
```

### Source Code

```text
src/main/java/unimagdeportes/
├── scheduler/ReservationLifecycleJob.java
├── service/sanction/SanctionService.java
├── repository/{ReservationRepository,AppUserRepository}.java
├── model/ReservationStatus.java
└── event/SanctionAppliedEvent.java

src/test/java/unimagdeportes/{integration/sanctions,unit/sanctions}/
```

**Structure Decision**: el job solo orquesta; la lógica vive en `SanctionService`. No se crean tablas nuevas.

## Reglas de negocio

1. **Inasistencia** [RN-37]: sin check-in al cerrar la ventana → `NO_SHOW`, horas contadas y aviso.
2. **Suspensión** [RN-38]: 3 inasistencias en 30 días → 7 días.
3. **Ventana rodante** [OQ-07]: conteo en 30 días.
4. **Reinicio** [OQ-07]: la suspensión reinicia el contador a cero.
5. **Reservas futuras** [RN-40]: se mantienen; no puede crear nuevas.
6. **Aviso** [RN-39]: correo con la fecha de reactivación.
7. **Idempotencia** [D-07]: el job puede reintentarse sin duplicar estados.

## Contrato REST

No aplica: UC10 es interno (scheduler), sin endpoints.

## Estrategia de testing

- Integración: inasistencia, tolerancia e idempotencia en T008.
- Integración: suspensión, ventana y reservas futuras en T014/T015.
- Aceptación: CE-001..CE-003 [SPEC 010].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01, UC03, UC05 y la base de UC11-H2.
- [ ] **T002** Agregar la consulta de reservas `CONFIRMED` con ventana cerrada.
- [ ] **T003** Agregar la consulta de inasistencias en ventana rodante.

### Phase 2: Foundational

- [ ] **T004** Implementar `SanctionService.markNoShow()`.
- [ ] **T005** Publicar `SanctionAppliedEvent`.
- [ ] **T006** Implementar `ReservationLifecycleJob` (idempotente).
- [ ] **T007** Registrar auditoría de la inasistencia.

### Phase 3: User Stories

- [ ] **T008** [US1/P1] Integración de inasistencia al cerrar la ventana.
- [ ] **T009** [US1/P1] Integración de reserva en tolerancia e idempotencia.
- [ ] **T010** [US2/P2] Implementar `evaluateSuspension()` (3 en 30 días → 7 días).
- [ ] **T011** [US2/P2] Reiniciar el contador al suspender.
- [ ] **T012** [US2/P2] Actualizar `suspended_until` y conservar reservas futuras.
- [ ] **T013** [US2/P2] Publicar el aviso de suspensión.
- [ ] **T014** [US2/P2] Integración de suspensión.
- [ ] **T015** [US2/P2] Integración de ventana rodante y reservas futuras.

### Phase 4: Polish

- [ ] **T016** Revisar logs y métricas del job.
- [ ] **T017** Verificar idempotencia con ejecuciones simultáneas y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001 → T002/T003 → T004..T007 → T008/T009 → T010..T015 → T016/T017`.

UC10 depende de UC01, UC03, UC05 y la base de UC11-H2. UC09 consume el indicador de suspendidos.

## Notes

- `[US1]` y `[US2]` son secuenciales: la suspensión necesita inasistencias registradas.
- No hay endpoint propio.
- La ventana de 30 días es rodante y se reinicia al suspender (OQ-07).

## Checklist de auto-revisión

- [ ] Alineado con SPEC 010.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Job idempotente y con `Clock` inyectado.
- [ ] Suspensión reinicia el contador.
