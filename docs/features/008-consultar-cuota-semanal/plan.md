# Implementation Plan: UC08 - Consultar cuota semanal

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC08 implementa la consulta de horas usadas, disponibles y fecha de reinicio de la cuota semanal [SPEC 008 RF-001..RF-007]. La cuota se **deriva** de las reservas que cuentan en la semana de la fecha de la franja; no hay tabla de saldo ni job de reinicio [PLAN D-06]. El reinicio semanal es el resultado de calcular sobre la nueva semana.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `QuotaService`, `QuotaController` | T004, T006 |
| RF-003 | cálculo por estado de reserva | T004 |
| RF-004 | límite configurado (UC06) | T004 |
| RF-005, RF-006 | `WeekCalculator` | T002, T010 |
| RF-007 | semana de la fecha de la franja | T004, T010 |
| RNF-001..RNF-003 | DTO, zona horaria | T002, T005 |
| CE-001..CE-003 | aceptación | T007, T008, T012 |
| HU1, HU2 | consulta y reinicio | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; lectura de `reservation` y `usage_parameters`.
**Messaging**: sin eventos.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: consulta agregada rápida y coherente con las reservas.
**Constraints**: 4 h por defecto (OQ-12); semana de la franja (RN-17); lunes 00:00 America/Bogota (RNF-07); sin saldo persistido (D-06).
**Scale/Scope**: 1 endpoint, sin migraciones, 7 RF + 3 RNF + 3 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/008-consultar-cuota-semanal/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/quota/GET-quota-me.md
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/quota/QuotaController.java
├── service/quota/QuotaService.java
├── service/policy/WeekCalculator.java
├── repository/ReservationRepository.java
└── dto/quota/ · mapper/quota/QuotaMapper.java

src/test/java/unimagdeportes/{contract/quota,integration/quota,unit/policy}/
```

**Structure Decision**: sin migraciones propias; reutiliza el repositorio de reservas de UC03. `WeekCalculator` es pura.

## Reglas de negocio

1. **Horas que cuentan** [sección 5.4]: confirmada, en uso, completada, cancelada tardía e inasistencia.
2. **No cuentan** [sección 5.4]: cancelada a tiempo y cancelada por administrador.
3. **Semana** [RN-17]: la de la fecha de la franja, no la de creación.
4. **Cálculo** [RN-16, RN-18]: usadas y disponibles sobre el límite configurado.
5. **Reinicio** [RN-19, D-06]: se cumple al calcular sobre la nueva semana; sin job.
6. **Zona horaria** [RNF-07]: lunes 00:00 America/Bogota.

## Contrato REST

Fuente: [`contracts/quota/GET-quota-me.md`](../../techincal-plan/contracts/quota/GET-quota-me.md). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato `GET /quota/me` en T007.
- Integración de estados y semanas en T008/T012.
- Unitario de `WeekCalculator` en T009.
- Aceptación: CE-001..CE-003 [SPEC 008].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01, UC03 y UC06.

### Phase 2: Foundational

- [ ] **T002** Implementar `WeekCalculator`.
- [ ] **T003** Agregar la consulta de horas por semana al repositorio.

### Phase 3: User Stories

- [ ] **T004** [US1/P1] Implementar `QuotaService`.
- [ ] **T005** [US1/P1] Crear DTO y `QuotaMapper`.
- [ ] **T006** [US1/P1] Implementar `GET /quota/me`.
- [ ] **T007** [US1/P1] Prueba de contrato.
- [ ] **T008** [US1/P1] Integración de estados que cuentan y no cuentan.
- [ ] **T009** [US1/P1] Prueba unitaria de `WeekCalculator`.
- [ ] **T010** [US2/P2] Calcular la semana por la fecha de la franja y exponer `resetAt`.
- [ ] **T011** [US2/P2] Verificar que no existe saldo ni job de reinicio.
- [ ] **T012** [US2/P2] Integración de cambio de semana y reservas anticipadas.

### Phase 4: Polish

- [ ] **T013** Revisar ejemplos contra la sección 9 del contexto.
- [ ] **T014** Revisar rendimiento de la consulta agregada y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001 → T002/T003 → T004..T009 → T010..T012 → T013/T014`.

UC08 depende de UC01, UC03 y UC06. US1 y US2 comparten el mismo cálculo.

## Notes

- `[US1]` y `[US2]` se prueban por separado.
- La cuota se deriva; no se almacena (D-06).
- No hay migración propia.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 008 y el contrato de cuota.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Semana por fecha de franja y sin saldo persistido.
- [ ] Estados que cuentan según la sección 5.4.
