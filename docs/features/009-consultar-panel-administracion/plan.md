# Implementation Plan: UC09 - Consultar panel de administración

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC09 implementa el panel del Administrador: ocupación por espacio y franja con filtros, e indicadores de uso con períodos fijos semanal y mensual seleccionables en el frontend [SPEC 009 RF-001..RF-004]. Ambas consultas son de solo lectura.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `OccupancyService`, `ReportController` | T005, T006 |
| RF-003 | `IndicatorService` | T009 |
| RF-004 | períodos semanal/mensual | T010 |
| RNF-001..RNF-003 | zona horaria, WCAG, auditoría | T002, T006 |
| CE-001..CE-004 | aceptación | T007, T008, T012, T013 |
| HU1, HU2 | ocupación e indicadores | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.2.0: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; consultas sobre `reservation`, `institutional_block`, `space` y `audit_log` (índice `audit_log_action_created_idx`).
**Messaging**: sin eventos.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: filtros eficientes por fecha, disciplina y espacio.
**Constraints**: solo lectura; períodos semanal y mensual (OQ-08); America/Bogota (RNF-07); rol `ADMIN`.
**Scale/Scope**: 2 endpoints, sin tablas nuevas (un índice en la migración compartida), 4 RF + 3 RNF + 4 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/009-consultar-panel-administracion/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/reports/   # 2 contratos
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/report/ReportController.java
├── service/report/{OccupancyService,IndicatorService}.java
├── service/{reservation,block,space,audit,sanction}/  # servicios propietarios
└── dto/report/ReportDtos.java · mapper/report/ReportMapper.java

src/test/java/unimagdeportes/{contract/reports,integration/reports}/
```

**Structure Decision**: sin tablas propias; el módulo `report` solo lee datos de otros módulos mediante sus services. Para los indicadores se añadió `audit_log_action_created_idx` a `V1__shared_schema.sql`.

**Bases existentes:** editar V1 cambia su checksum y Flyway puede rechazar una base donde ya se aplicó. Se requiere coordinar su recreación, respaldando y conservando previamente los datos necesarios; la modificación no actualiza automáticamente instalaciones existentes. Véase el README.

### Decisiones de implementación

- **Ocupación**: `OccupancyService` reutiliza `SlotCatalogPolicy` (UC02) con `ReservationService.overlappingOccupancy` y `BlockService.overlapping`. `reservationId` se informa solo en las franjas `OCCUPIED`; el bloqueo tiene precedencia, como en el calendario. Se listan todos los espacios, también los que están en mantenimiento, ordenados por nombre. Un `spaceId` inexistente devuelve `spaces` vacío. La fecha no tiene horizonte.
- **Indicadores**: el período es la semana (lunes 00:00) o el mes calendario en curso, `[from, to)` en America/Bogota, según el `Clock`. Fuente, por indicador:
  - `reservations`: auditorías `RESERVATION_CREATED` del período.
  - `lateCancellations`: reservas con `RESERVATION_CANCELLED` en el período y estado `CANCELLED_LATE`. La auditoría de UC04 no distingue el tipo de cancelación, por eso se cruza con el estado.
  - `noShows`: auditorías `NO_SHOW_REGISTERED` del período.
  - `suspendedUsers`: usuarios distintos con intervalos de suspensión solapados con el período, consultados mediante `SanctionService` y `monthly_suspension`.
- **Fuente de suspendidos**: auditorías `USER_SUSPENDED` con su fecha de fin en `detail`; para eventos anteriores sin instantánea se usa la última marca mensual creada no después del evento. Una marca sin auditoría no se cuenta.
- **Fronteras de módulo**: `AuditService` expone `count` y `distinctEntities`; `ReservationService` expone `countWithStatus`. El módulo `report` no usa repositorios ajenos.
- **Mapper**: `ReportMapper` es una interfaz MapStruct [D-24].

## Reglas de negocio

1. **Ocupación** [RF-14]: por espacio y franja, con filtros por fecha, disciplina y espacio.
2. **Estados** [RN-07]: un espacio compartido aparece ocupado para todas sus disciplinas.
3. **Indicadores** [RF-15]: reservas, cancelaciones tardías, inasistencias y suspendidos.
4. **Períodos** [OQ-08]: fijos semanal y mensual; el frontend elige.
5. **Definiciones** [RN-22, RN-37]: cancelación tardía e inasistencia según el contexto.
6. **Zona horaria** [RNF-07]: America/Bogota.

## Contrato REST

Fuente: [`contracts/reports/`](../../techincal-plan/contracts/README.md) (2 archivos). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: ocupación e indicadores en T007/T012.
- Integración: filtros, períodos y métricas en T008/T013.
- Aceptación: CE-001..CE-004 [SPEC 009].

## Implementation Phases

### Phase 1: Setup

- [x] **T001** Verificar prerrequisitos: UC01, UC03, UC07 y UC10.

### Phase 2: Foundational

- [x] **T002** Crear los DTO de `dto/report/`.
- [x] **T003** Crear `ReportMapper`.
- [x] **T004** Agregar consultas de ocupación, bloqueos y agregados a los repositorios.

### Phase 3: User Stories

- [x] **T005** [US1/P1] Implementar `OccupancyService`.
- [x] **T006** [US1/P1] Implementar `GET /admin/occupancy`.
- [x] **T007** [US1/P1] Prueba de contrato de ocupación.
- [x] **T008** [US1/P1] Integración de filtros y espacio compartido.
- [x] **T009** [US2/P2] Implementar `IndicatorService`.
- [x] **T010** [US2/P2] Validar `period` y calcular `from`/`to`.
- [x] **T011** [US2/P2] Implementar `GET /admin/indicators`.
- [x] **T012** [US2/P2] Prueba de contrato de indicadores.
- [x] **T013** [US2/P2] Integración de períodos y métricas.

### Phase 4: Polish

- [ ] **T014** Revisar índices y planes de consulta.
- [x] **T015** Verificar rol `ADMIN` y ejecutar `./mvnw clean verify`.

**Avance:** los dos endpoints están implementados.

- `OccupancyContractTest`: forma de la respuesta, validación de `date`, `discipline` y `spaceId`, y rol `ADMIN` vigente (401/403).
- `OccupancyIntegrationTest`: espacio compartido ocupado para todas sus disciplinas (RN-07), filtros por disciplina, espacio y fecha, y estados que liberan el espacio (CE-001, CE-002).
- `IndicatorsContractTest`: forma de la respuesta, períodos semanal y mensual, `INVALID_PERIOD`, `VALIDATION_ERROR` y rol.
- `IndicatorsIntegrationTest`: límites inclusivo y exclusivo de cada período, eventos fuera del período, suspensiones repetidas contadas una vez, y los flujos reales de cancelación tardía, inasistencia y suspensión (CE-003, CE-004).
- `SharedSchemaTest` comprueba el índice `audit_log_action_created_idx` (T014). Ocupación y bloqueos usan índices o restricciones existentes por espacio e intervalo. No se midieron planes de consulta con volumen real.

`./mvnw clean verify` terminó con 214 pruebas sin fallos. RNF-002 (WCAG) depende del cliente React externo.

## Dependencies & Execution Order

`T001 → T002..T004 → T005..T013 → T014/T015`.

UC09 depende de UC01, UC03, UC07 y UC10. US1 y US2 son independientes entre sí.

## Notes

- `[US1]` y `[US2]` son independientes.
- Períodos fijos semanal y mensual (OQ-08).
- No se crean tablas nuevas.
- Las nuevas auditorías `USER_SUSPENDED` conservan su fecha de fin en `detail`. Para eventos anteriores sin esa instantánea se usa la última marca mensual creada no después del evento, si existe; no se inventan fechas históricas. `IndicatorsIntegrationTest` prueba instantáneas sin marca y excluye marcas sin auditoría.

## Checklist de auto-revisión

- [x] Alineado con SPEC 009 y `contracts/reports/`: suspendidos derivados de auditoría y fecha de fin.
- [x] RF/RNF/CE/HU trazados.
- [x] Solo lectura y rol `ADMIN`.
- [x] Períodos y filtros correctos.
