# Implementation Plan: UC03 - Reservar espacio deportivo

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC03 es la creación de reservas con confirmación automática: elegir espacio, inicio y una o dos franjas consecutivas, validar suspensión, anticipación, tolerancia, bloqueos, solape y cuota, y bloquear la franja en una sola transacción para garantizar cero cruces [SPEC 003 RF-001..RF-012]. La solicitud puede incluir implementos; su validación de stock se integra en UC13.

Enfoque técnico: `ReservationService` transaccional que bloquea la fila del espacio, valida con políticas puras y usa las restricciones de exclusión de PostgreSQL [PLAN D-04, D-05]. La cuota se deriva de las reservas (D-06).

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `ReservationService`, `AvailabilityService` | T008, T011 |
| RF-003 | validaciones de suspensión, disponibilidad y cuota | T009 |
| RF-004, RF-005 | transacción + restricciones de exclusión | T003, T008 |
| RF-006 | consulta de solape propio | T004, T009 |
| RF-007 | máximo 2 franjas consecutivas | T005, T009 |
| RF-008 | `BookingWindowPolicy` | T005 |
| RF-009 | `QuotaPolicy`, `QuotaService` | T006, T007 |
| RF-010 | tolerancia y check-in posterior (UC05) | T005, T009 |
| RF-011 | confirmación inmediata | T008 |
| RF-012 | evento de comprobante (UC11) | T012 |
| RF-013 | integración de implementos en UC13 | T008, T009 |
| RNF-001..RNF-005 | flujo, zona horaria, auditoría | T002, T013 |
| CE-001..CE-003 | aceptación | T014..T018 |
| HU1 | reserva de una o dos franjas | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; `reservation` + `space`, `institutional_block`, `usage_parameters`; `btree_gist` para exclusiones.
**Messaging**: evento de comprobante después del commit [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (concurrencia y restricciones).
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: confirmación inmediata y cero cruces con solicitudes simultáneas (RN-10).
**Constraints**: 1–2 franjas (RN-15); 7 días (OQ-12); cuota 4 h (RN-18); sin solape propio (RN-11); sin suspensión (RN-40); America/Bogota (RNF-07).
**Scale/Scope**: 1 endpoint, 1 entidad, esquema en la migración única compartida, 13 RF + 5 RNF + 3 CE + 1 HU.

## Project Structure

### Documentation

```text
docs/features/003-reservar-espacio-deportivo/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/reservations/POST-reservations.md
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/reservation/ReservationController.java
├── service/reservation/ReservationService.java
├── service/quota/QuotaService.java
├── service/policy/{BookingWindowPolicy,QuotaPolicy}.java
├── repository/ReservationRepository.java
├── service/space/SpaceService.java
├── model/{Reservation,ReservationStatus}.java
├── dto/reservation/ · mapper/reservation/ReservationMapper.java
└── event/ReservationCreatedEvent.java

src/main/resources/db/migration/ (migración única compartida)
src/test/java/unimagdeportes/{contract/reservation,integration/reservation,unit/policy}/
```

**Structure Decision**: la reserva vive en `reservation`; la cuota se calcula en `QuotaService` y no se almacena [D-06]. La transacción bloquea el espacio y luego inserta [D-05].

## Reglas de negocio

1. **Cero cruces** [RN-10]: verificación y bloqueo en una transacción; gana el primero.
2. **Solape propio** [RN-11]: un usuario no puede tener reservas solapadas.
3. **Confirmación** [RN-12, RN-13]: solo si correo, suspensión, disponibilidad y cuota son válidos; sin aprobación manual.
4. **Anticipación** [RN-14, OQ-12]: hasta 7 días; tolerancia de 10 minutos tras el inicio.
5. **Duración** [RN-15]: 1 o 2 franjas consecutivas.
6. **Cuota** [RN-16..RN-18]: 4 h por semana de la fecha de la franja; rechaza e informa las horas restantes.
7. **Espacio compartido** [RN-07]: la reserva bloquea el espacio completo.
8. **Implementos** [RN-63, RN-65]: opcionales; la validación de stock se integra en UC13.

## Contrato REST

Fuente: [`contracts/reservations/POST-reservations.md`](../../techincal-plan/contracts/reservations/POST-reservations.md). Convenciones y errores: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato `POST /reservations` en T014.
- Integración: reserva válida, dos franjas, concurrencia, reglas y ventana en T015..T018.
- Unitario: `QuotaPolicy` y `BookingWindowPolicy` en T019.
- Aceptación: CE-001 (integridad), CE-002 (reglas), CE-003 (usabilidad) [SPEC 003].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar UC01, UC06, UC07-H1, la tabla de bloqueos compartida y la base de consulta de UC02; integrar UC07-H2 después de UC03.

### Phase 2: Foundational

- [x] **T002** Crear `Reservation` y `ReservationStatus`.
- [x] **T003** Verificar las dos restricciones de exclusión de reservas en la migración única.
- [ ] **T004** Crear `ReservationRepository` (solape y horas por semana).
- [ ] **T005** Implementar `BookingWindowPolicy`.
- [ ] **T006** Implementar `QuotaPolicy`.
- [ ] **T007** Implementar `QuotaService`.

### Phase 3: User Stories

- [ ] **T008** [US1/P1] Implementar `ReservationService`: adquirir primero el bloqueo asesor global de §4.1 del plan general, bloquear usuario y espacio, validar cuota por semana, conservar reglas y luego insertar; integrar implementos en UC13.
- [ ] **T009** [US1/P1] Validar suspensión, mantenimiento, bloqueos, solape y cuota.
- [ ] **T010** [US1/P1] Crear DTO y `ReservationMapper`.
- [ ] **T011** [US1/P1] Implementar `POST /reservations` en `ReservationController`.
- [ ] **T012** [US1/P1] Publicar `ReservationCreatedEvent` después del commit.
- [ ] **T013** [US1/P1] Registrar auditoría y logging.
- [ ] **T014** [US1/P1] Prueba de contrato.
- [ ] **T015** [US1/P1] Integración: reserva válida y dos franjas.
- [ ] **T016** [US1/P1] Integración: concurrencia sobre la misma franja.
- [ ] **T017** [US1/P1] Integración: cuota, solape y suspensión.
- [ ] **T018** [US1/P1] Integración: tolerancia y ventana de anticipación.
- [ ] **T019** [US1/P1] Pruebas unitarias de políticas.

### Phase 4: Polish

- [ ] **T020** Revisar mensajes de error contra el catálogo de contratos.
- [ ] **T021** Revisar índices de solape/cuota y ejecutar `./mvnw clean verify`.

**Avances parciales:** existe `ReservationRepository` sin las consultas de T004 y `ReservationService` solo ofrece lectura; `ReservationLock` está creado, pero aún no se integra al flujo de reserva. `SharedSchemaTest` comprueba ambas restricciones en PostgreSQL, no la concurrencia ni el endpoint.

## Dependencies & Execution Order

`T001 → T002..T007 → T008..T013 → T014..T019 → T020/T021`.

UC03 depende de UC01, UC06, UC07-H1 y el esquema compartido, y de la base de UC02; UC07-H2 se integra después de UC03. UC13 depende de UC03 para adjuntar implementos.

## Notes

- `[US1]` etiqueta la única historia de la SPEC 003.
- La validación de stock no se implementa aquí; UC13 la añade en la misma transacción.
- La cuota se deriva; no hay tabla de saldo (D-06).

## Checklist de auto-revisión

- [ ] Alineado con SPEC 003 y el contrato de reserva.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Cero cruces y solape cubiertos por pruebas.
- [ ] Cuota derivada y sin saldo persistido.
