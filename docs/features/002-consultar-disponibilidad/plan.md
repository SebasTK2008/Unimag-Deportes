# Implementation Plan: UC02 - Consultar disponibilidad

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC02 es la consulta **REST + SSE, de solo lectura** del calendario de disponibilidad por espacio y disciplina: franjas libres, ocupadas y bloqueadas, con actualización en tiempo real [SPEC 002 RF-001..RF-008]. Se resuelve con datos de UC07 (espacios y bloqueos) y UC03 (reservas); el calendario muestra siete fechas, desde hoy hasta hoy + 6 inclusive en America/Bogota, y exige sesión [OQ-09, OQ-14]. El endpoint conserva una fecha por petición.

Los cambios de disponibilidad se notifican por SSE y el cliente recarga el calendario por REST [PLAN D-11]. UC02 no crea tablas ni muta datos. D-27 añade la cancelación de bloqueos como productor post-commit; `BlockService.overlapping` excluye `CANCELLED`, mientras la consulta histórica administrativa los conserva. Probar otros impedimentos y rollback; el calendario público no necesita `blockId`.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001 | `AvailabilityService`, `AvailabilityController` | T007, T009 |
| RF-002 | emisor SSE + publicación de eventos | T010, T011 |
| RF-003 | consultas de ocupación en repositorios | T006 |
| RF-004, RF-005 | filtro de estado y motivo de bloqueo | T007 |
| RF-006 | `SlotCatalogPolicy` (tolerancia) | T005 |
| RF-007 | invocación interna desde UC03 | T007, T009 |
| RF-008 | sesión y 7 fechas contando hoy (hoy…hoy + 6) | T008 |
| RNF-001 | emisor SSE + publicación de eventos | T010, T011 |
| RNF-002 | cliente React móvil y accesible, en otro repositorio | verificación externa |
| RNF-003 | DTO, `Clock`, zona horaria | T003, T007 |
| CE-001..CE-004 | aceptación | T012, T013 |
| HU1 | calendario en tiempo real | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.2.0: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; lectura de `space`, `space_discipline`, `reservation` e `institutional_block`; sin migraciones.
**Messaging**: SSE; sin colas [D-11].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico; cliente React en otro repositorio que consume SSE con `fetch` [D-25, PLAN §5.3].
**Performance Goals**: reflejar cambios al instante (RNF-04).
**Constraints**: 7 fechas contando hoy, hasta hoy + 6 inclusive (OQ-09); sesión (OQ-14); espacio compartido completo (RN-07); mantenimiento no reservable (RN-09); tolerancia (RN-27, RN-28); motivo visible (RN-44); America/Bogota (RNF-07).
**Scale/Scope**: 3 endpoints, sin migraciones, 8 RF + 3 RNF + 4 CE + 1 HU.

## Project Structure

### Documentation

```text
docs/features/002-consultar-disponibilidad/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/availability/   # 3 contratos
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/availability/AvailabilityController.java
├── service/availability/{AvailabilityService,AvailabilityStream}.java
├── service/policy/SlotCatalogPolicy.java
├── service/{space,reservation,block}/  # servicios propietarios
└── dto/availability/ · mapper/availability/AvailabilityMapper.java

src/test/java/unimagdeportes/{controller/availability,integration/availability,service/availability,service/policy}/
```

**Structure Decision**: módulo de solo lectura que consulta otros módulos únicamente mediante sus services y publica eventos SSE [PLAN §3.4]. Las franjas se derivan del horario y no se persisten [§12].

## Reglas de negocio

1. **Estados** [RN-10, RN-44]: `FREE`, `OCCUPIED`, `BLOCKED` (con motivo visible).
2. **Espacio compartido** [RN-07]: la reserva bloquea el espacio completo para todas sus disciplinas.
3. **Mantenimiento** [RN-09]: no aparece como reservable.
4. **Tolerancia** [RN-27, RN-28]: una franja libre iniciada sigue reservable hasta el fin de la tolerancia.
5. **Horizonte** [OQ-09]: desde hoy hasta hoy + 6 días inclusive en America/Bogota (7 fechas); fuera de rango → `OUTSIDE_CALENDAR_HORIZON` 400.
6. **Sesión** [OQ-14]: la consulta exige sesión.
7. **Tiempo real** [RNF-04]: cambios por SSE; el cliente recarga por REST.
8. **Zona horaria** [RNF-07]: America/Bogota.

## Contrato REST

Fuente: [`contracts/availability/`](../../techincal-plan/contracts/README.md) (3 archivos, incluido el flujo SSE). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: los 3 endpoints (estados, errores y eventos) en T012.
- Integración: estados, espacio compartido, mantenimiento, horizonte y sesión en T013.
- Aceptación: CE-001..CE-004 [SPEC 002].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar UC01, UC06 y UC07-H1; integrar ocupación y bloqueos cuando UC03 y UC07-H2 estén disponibles, sin consultar tablas aún inexistentes.
- [ ] **T002** Verificar la configuración SSE con `fetch` y encabezado `Authorization`.

### Phase 2: Foundational

- [x] **T003** Crear los DTO de `dto/availability/`.
- [x] **T004** Crear `AvailabilityMapper`.
- [x] **T005** Implementar `SlotCatalogPolicy` (franjas, estados y tolerancia).
- [x] **T006** Agregar las consultas de ocupación y bloqueos (solo lectura).

**Avance parcial:** el esquema compartido V1 ya incluye reservas y bloqueos. `BlockService.overlapping` y `ReservationService.overlappingOccupancy` proporcionan las lecturas integradas en `AvailabilityService` (T006/T007). Existen el controlador con tres endpoints, la validación de sesión y horizonte, y `AvailabilityStream` (T008–T010); la integración con PostgreSQL y el filtro de mantenimiento se verificaron en ejecución. El consumo con `fetch` sigue sin verificarse.

Los cambios de espacio, los bloqueos, la creación de reservas y la cancelación ordinaria de UC04 publican `AvailabilityChangedEvent` para envío después del commit. T011 sigue pendiente de la verificación completa del flujo. `ReservationCreationService` consulta ocupación y bloqueos mediante `AvailabilityService` dentro de la transacción; la presentación y actualización automática tras conflicto requieren el cliente externo. El catálogo alinea las franjas a horas exactas posteriores o iguales a la apertura.

Hay pruebas unitarias, de controlador e integración con PostgreSQL, incluido un bloqueo SSE; T012/T013 siguen pendientes de cobertura completa y de los flujos que dependen de UC03. RNF-002 y el uso real de `fetch` requieren verificar el cliente React del otro repositorio. T015 sigue pendiente: la prueba simulada con varias suscripciones no sustituye una revisión de rendimiento en condiciones reales.

### Phase 3: User Stories

- [ ] **T007** [US1/P1] Implementar `AvailabilityService` (espacios y disponibilidad por fecha).
- [ ] **T008** [US1/P1] Validar sesión y las 7 fechas hoy…hoy + 6; probar ambos extremos y rechazar ayer y hoy + 7.
- [ ] **T009** [US1/P1] Implementar `AvailabilityController` con los 3 endpoints.
- [ ] **T010** [US1/P1] Implementar el emisor SSE.
- [ ] **T011** [US1/P1] Publicar eventos desde reserva, cancelación de reserva, creación/cancelación de bloqueo y cambio de espacio.
- [ ] **T012** [US1/P1] Pruebas de contrato.
- [ ] **T013** [US1/P1] Pruebas de integración.

### Phase 4: Polish

- [x] **T014** Verificar que mantenimiento no aparece en `GET /spaces`.
- [ ] **T015** Revisar rendimiento con varias suscripciones SSE y `./mvnw clean verify`.

## Dependencies & Execution Order

`T001/T002 → T003..T006 → T007..T011 → T012/T013 → T014/T015`.

La base de UC02 depende de UC01, UC06 y UC07-H1; la integración de reservas y bloqueos se completa con UC03 y UC07-H2. UC03 debe consumir la consulta base de UC02. El esquema actual incluye las tablas en `V1__shared_schema.sql`; UC02 no agrega migraciones.

## Notes

- `[US1]` etiqueta la única historia de la SPEC 002.
- El token nunca viaja en la URL del SSE.
- Sin tablas ni migraciones nuevas.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 002 y `contracts/availability/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Horizonte de 7 fechas contando hoy (hoy…hoy + 6) y sesión aplicados.
- [ ] Solo lectura y SSE con `fetch`.
