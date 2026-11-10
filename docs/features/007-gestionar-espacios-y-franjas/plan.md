# Implementation Plan: UC07 - Gestionar espacios y franjas

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC07 implementa la gestión de espacios (nombre, disciplinas, aforo, requisitos, horario y estado) y el bloqueo de franjas por clases, eventos o mantenimiento [SPEC 007 RF-001..RF-011]. Los bloqueos recurrentes se materializan en una fila por franja, tienen prioridad sobre las reservas libres y cancelan las afectadas sin descontar cuota.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `SpaceService` | T007, T011 |
| RF-003 | franjas derivadas del horario | T007 |
| RF-004, RF-005 | estado y disciplinas del espacio | T007, T012 |
| RF-006, RF-007 | `BlockService` (puntual/recurrente) | T008 |
| RF-008, RF-009 | cancelación de reservas afectadas | T009 |
| RF-010 | motivo visible en el calendario (UC02) | T009 |
| RF-011 | reautenticación en bloqueos | T018 |
| RNF-001..RNF-003 | zona horaria, WCAG, auditoría | T013, T020 |
| CE-001..CE-006 | aceptación | T014, T021 |
| HU1, HU2 | espacios y bloqueos | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; `space`, `space_discipline` e `institutional_block`.
**Messaging**: evento de aviso a afectados después del commit [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: operaciones administrativas poco frecuentes; consultas de disponibilidad eficientes.
**Constraints**: franjas de 60 min (RN-08); mantenimiento no reservable (RN-09); prioridad de bloqueos (RN-42); cancelación sin cuota (RN-43); reautenticación (D-21); America/Bogota (RNF-07).
**Scale/Scope**: 4 endpoints, 3 entidades, 3 migraciones (`V6..V8`), 11 RF + 3 RNF + 6 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/007-gestionar-espacios-y-franjas/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/spaces/   # 3 contratos
docs/techincal-plan/contracts/blocks/   # 1 contrato
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/space/SpaceController.java
├── controller/block/BlockController.java
├── service/space/SpaceService.java
├── service/block/BlockService.java
├── repository/{SpaceRepository,SpaceDisciplineRepository,InstitutionalBlockRepository}.java
├── model/{Space,SpaceDiscipline,InstitutionalBlock,SpaceStatus,BlockType}.java
├── dto/{space,block}/ · mapper/{space,block}/

src/main/resources/db/migration/
├── V6__create_space.sql
├── V7__create_space_discipline.sql
└── V8__create_institutional_block.sql

src/test/java/unimagdeportes/{contract/{spaces,blocks},integration/{spaces,blocks},unit/blocks}/
```

**Structure Decision**: espacios y bloqueos se separan en los módulos `space` y `block`; se comunican por sus services [PLAN §3.4].

## Reglas de negocio

1. **Atributos** [RN-06]: nombre, disciplinas, aforo, requisitos, horario y estado.
2. **Franjas** [RN-08]: bloques de 60 min dentro del horario; no se persisten.
3. **Mantenimiento** [RN-09]: no reservable.
4. **Espacio compartido** [RN-07]: la reserva bloquea el espacio completo.
5. **Bloqueos** [RN-41]: clases, eventos o mantenimiento; puntuales o recurrentes.
6. **Prioridad** [RN-42]: el bloqueo prevalece sobre la reserva libre.
7. **Cancelación** [RN-43]: las reservas afectadas pasan a `CANCELLED_BY_ADMIN` sin descontar cuota y se avisa.
8. **Recurrencia** [OQ-06]: materializada por franja con `recurrence_group`; no se edita por grupo.
9. **Desactivar** [OQ-12]: equivale a `MAINTENANCE`; no cancela reservas existentes (OQ-05).

## Contrato REST

Fuente: [`contracts/spaces/`](../../techincal-plan/contracts/README.md) y [`contracts/blocks/`](../../techincal-plan/contracts/README.md) (4 archivos). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: espacios y bloqueos en T014/T021.
- Integración: creación/edición/mantenimiento, recurrencia, cascada y reautenticación en T014/T021.
- Unitario: materialización de recurrencia en T023.
- Aceptación: CE-001..CE-006 [SPEC 007].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01 y UC03.

### Phase 2: Foundational

- [ ] **T002** Crear `Space` y `SpaceStatus`.
- [ ] **T003** Crear `SpaceDiscipline`.
- [ ] **T004** Crear `InstitutionalBlock` y `BlockType`.
- [ ] **T005** Crear las migraciones `V6..V8`.
- [ ] **T006** Crear los repositorios de espacio, disciplina y bloqueo.
- [ ] **T007** Implementar `SpaceService`.
- [ ] **T008** Implementar `BlockService` con materialización de recurrencia.
- [ ] **T009** Implementar la cancelación de reservas afectadas.

### Phase 3: User Stories

- [ ] **T010** [US1/P1] Crear DTO y mapper de espacio.
- [ ] **T011** [US1/P1] Implementar `GET/POST /admin/spaces` y `PUT /admin/spaces/{id}`.
- [ ] **T012** [US1/P1] Validar horario, disciplinas y aforo.
- [ ] **T013** [US1/P1] Registrar auditoría de espacios.
- [ ] **T014** [US1/P1] Pruebas de contrato e integración de espacios.
- [ ] **T015** [US2/P1] Crear DTO y mapper de bloqueo.
- [ ] **T016** [US2/P1] Implementar `POST /admin/blocks`.
- [ ] **T017** [US2/P1] Publicar el evento de aviso a afectados.
- [ ] **T018** [US2/P1] Exigir reautenticación (`REAUTH_REQUIRED`).
- [ ] **T019** [US2/P1] Registrar auditoría de bloqueos y cancelaciones.
- [ ] **T020** [US2/P1] Pruebas de contrato de bloqueos.
- [ ] **T021** [US2/P1] Integración de recurrencia y cascada.
- [ ] **T022** [US2/P1] Prueba unitaria de materialización.
- [ ] **T023** [US2/P1] Verificar que "desactivar" equivale a mantenimiento.

### Phase 4: Polish

- [ ] **T024** Verificar que los cambios de espacio no cancelan reservas existentes.
- [ ] **T025** Revisar rendimiento de recurrencias largas y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001 → T002..T009 → T010..T023 → T024/T025`.

UC07 depende de UC01 y UC03. US1 crea los espacios; US2 requiere espacios y reservas. UC02 consume espacios y bloqueos.

## Notes

- `[US1]` y `[US2]` pueden implementarse por separado (US2 requiere US1).
- Los bloqueos recurrentes se materializan; no se editan por grupo (OQ-06).
- El QR del espacio se implementa en UC05.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 007 y `contracts/spaces|blocks/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Cascada de cancelación sin cuota y con aviso.
- [ ] Reautenticación en bloqueos.
