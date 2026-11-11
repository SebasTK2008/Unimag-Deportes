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
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; consultas sobre `reservation`, `institutional_block`, `space` y `app_user`.
**Messaging**: sin eventos.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: filtros eficientes por fecha, disciplina y espacio.
**Constraints**: solo lectura; períodos semanal y mensual (OQ-08); America/Bogota (RNF-07); rol `ADMIN`.
**Scale/Scope**: 2 endpoints, sin migraciones, 4 RF + 3 RNF + 4 CE + 2 HU.

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
├── service/{reservation,block,space,auth}/  # servicios propietarios
└── dto/report/ · mapper/report/ReportMapper.java

src/test/java/unimagdeportes/{contract/reports,integration/reports}/
```

**Structure Decision**: sin migraciones propias; el módulo `report` solo lee datos de otros módulos.

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

- [ ] **T001** Verificar prerrequisitos: UC01, UC03, UC07 y UC10.

### Phase 2: Foundational

- [ ] **T002** Crear los DTO de `dto/report/`.
- [ ] **T003** Crear `ReportMapper`.
- [ ] **T004** Agregar consultas de ocupación, bloqueos y agregados a los repositorios.

### Phase 3: User Stories

- [ ] **T005** [US1/P1] Implementar `OccupancyService`.
- [ ] **T006** [US1/P1] Implementar `GET /admin/occupancy`.
- [ ] **T007** [US1/P1] Prueba de contrato de ocupación.
- [ ] **T008** [US1/P1] Integración de filtros y espacio compartido.
- [ ] **T009** [US2/P2] Implementar `IndicatorService`.
- [ ] **T010** [US2/P2] Validar `period` y calcular `from`/`to`.
- [ ] **T011** [US2/P2] Implementar `GET /admin/indicators`.
- [ ] **T012** [US2/P2] Prueba de contrato de indicadores.
- [ ] **T013** [US2/P2] Integración de períodos y métricas.

### Phase 4: Polish

- [ ] **T014** Revisar índices y planes de consulta.
- [ ] **T015** Verificar rol `ADMIN` y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001 → T002..T004 → T005..T013 → T014/T015`.

UC09 depende de UC01, UC03, UC07 y UC10. US1 y US2 son independientes entre sí.

## Notes

- `[US1]` y `[US2]` son independientes.
- Períodos fijos semanal y mensual (OQ-08).
- No se crean tablas nuevas.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 009 y `contracts/reports/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Solo lectura y rol `ADMIN`.
- [ ] Períodos y filtros correctos.
