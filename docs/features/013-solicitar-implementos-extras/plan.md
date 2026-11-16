# Implementation Plan: UC13 - Solicitar implementos extras

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC13 implementa el catálogo de implementos con existencias y la solicitud opcional de implementos al reservar, indicando cantidades [SPEC 013 RF-001..RF-006]. La disponibilidad de un implemento en una franja se deriva de las reservas activas que se solapan (`stock - comprometido`) y la validación ocurre en la misma transacción de la reserva [RN-65, RN-66, D-22, D-23].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `ReservationCreationService` + `EquipmentService` | T007, T009 |
| RF-003 | validación de stock en la transacción | T007 |
| RF-004 | solicitud opcional | T007, T016 |
| RF-005 | `EquipmentService` (catálogo) | T017 |
| RF-006 | consulta de disponibilidad | T006, T018 |
| RNF-001..RNF-002 | DTO de reserva; aceptación mobile-first/WCAG y pasos en el cliente React externo | T011 |
| CE-001..CE-006 | aceptación | T012..T016, T023 |
| HU1, HU2 | solicitud y catálogo | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; `equipment` y `reservation_equipment`; lectura de `reservation`.
**Messaging**: sin eventos propios; el comprobante se extiende en UC11.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (solapes de tiempo).
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: validación de stock dentro de la transacción de reserva.
**Constraints**: solo disponibles (RN-62); cantidades (RN-63); disponibilidad derivada (RN-65); sin saldo y canceladas liberan (RN-66); comprobante (OQ-20); disciplina sugerida (OQ-21); America/Bogota.
**Scale/Scope**: 4 endpoints, 2 entidades, esquema en la migración única compartida, 6 RF + 2 RNF + 6 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/013-solicitar-implementos-extras/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/equipment/   # 4 contratos
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/equipment/EquipmentController.java
├── service/equipment/EquipmentService.java
├── service/reservation/ReservationCreationService.java (integración)
├── repository/equipment/{EquipmentRepository,ReservationEquipmentRepository}.java
├── model/equipment/{Equipment,ReservationEquipment,ReservationEquipmentId,EquipmentStatus}.java
├── dto/equipment/ · mapper/equipment/EquipmentMapper.java

src/main/resources/db/migration/
└── V1__shared_schema.sql

src/test/java/unimagdeportes/{integration/equipment,integration/reservation}/
```

**Structure Decision**: el catálogo vive en `equipment`; la validación de stock se integra en `ReservationCreationService` dentro de la misma transacción [PLAN D-22, D-23]. La asociación se inserta mediante JDBC; la entidad y el repositorio JPA existen, pero no realizan esa inserción.

## Reglas de negocio

1. **Catálogo** [RN-62]: nombre, disciplina sugerida, stock y estado; solo disponibles con stock se solicitan.
2. **Solicitud** [RN-63]: opcional, en la reserva, con cantidades; puede haber varios implementos.
3. **Asociación** [RN-64]: los implementos quedan asociados a la reserva.
4. **Disponibilidad** [RN-65]: `stock` menos el máximo de unidades comprometidas simultáneamente durante el intervalo.
5. **Sin saldo** [RN-66, D-23]: se deriva; canceladas liberan stock.
6. **Comprobante** [OQ-20]: lista los implementos con su cantidad.
7. **Disciplina** [OQ-21]: solo sugerencia, sin restricción.
8. **Límite** [OQ-18]: la cantidad se acota por el stock de la franja.

## Contrato REST

Fuente: [`contracts/equipment/`](../../techincal-plan/contracts/README.md) (4 archivos). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: reserva con implementos y endpoints de catálogo/disponibilidad en T012/T023.
- Integración: stock insuficiente, solapes, liberación al cancelar y reserva sin implementos en T013..T016.
- Aceptación: CE-001..CE-006 [SPEC 013].

## Implementation Phases

### Phase 1: Setup

- [x] **T001** Verificar UC01, UC03, UC07-H1 y el esquema compartido; UC11-H1 solo es prerrequisito de la integración del comprobante en T010.

### Phase 2: Foundational

- [x] **T002** Crear `Equipment`, `EquipmentStatus` y `ReservationEquipment`.
- [x] **T003** Verificar las tablas de implementos en la migración única.
- [x] **T004** Crear `EquipmentRepository` y `ReservationEquipmentRepository`.
- [x] **T005** Implementar `EquipmentService` y su consulta de disponibilidad.
- [x] **T006** Agregar la consulta de cantidades comprometidas por ventana.

### Phase 3: User Stories

- [x] **T007** [US1/P2] Integrar la validación de stock en `ReservationCreationService` bajo el bloqueo asesor global de §4.1 del plan general y bloquear las filas de implementos en orden de id.
- [x] **T008** [US1/P2] Validar implementos inexistentes, inactivos, identificadores repetidos y cantidades; rechazar duplicados con `VALIDATION_ERROR`.
- [x] **T009** [US1/P2] Asociar implementos a la reserva.
- [x] **T010** [US1/P2] Extender el comprobante (UC11) con implementos.
- [x] **T011** [US1/P2] Extender DTO y mapper de reserva. La aceptación de los RNF de interfaz corresponde al cliente externo.
- [ ] **T012** [US1/P2] Prueba de contrato de `POST /reservations` con implementos.
- [x] **T013** [US1/P2] Integración de stock insuficiente.
- [ ] **T014** [US1/P2] Integración de reservas solapadas y simultáneas por el último stock, incluyendo rangos consecutivos y compromisos simultáneos máximos.
- [x] **T015** [US1/P2] Integración de liberación al cancelar.
- [x] **T016** [US1/P2] Integración de reserva sin implementos.
- [x] **T017** [US2/P2] Implementar `EquipmentService` (listar, crear, editar, desactivar).
- [x] **T018** [US2/P2] Implementar `GET /equipment` y `GET /equipment/availability`.
- [x] **T019** [US2/P2] Implementar `POST /admin/equipment` y `PUT /admin/equipment/{id}`.
- [x] **T020** [US2/P2] Restringir administración al rol `ADMIN`.
- [x] **T021** [US2/P2] Validar stock no negativo y nombre único; adquirir el mismo bloqueo global y rechazar reducción por debajo del máximo compromiso simultáneo futuro; probar carreras con reservas.
- [x] **T022** [US2/P2] Verificar que desactivar no afecta reservas existentes.
- [ ] **T023** [US2/P2] Pruebas de contrato del catálogo.

### Phase 4: Polish

- [ ] **T024** Revisar índices de solape por implemento y rango.
- [x] **T025** Verificar que no se almacena saldo por franja y ejecutar `./mvnw clean verify`.

**Avances verificados:** existen las entidades, repositorios, servicio y cuatro endpoints del catálogo. La creación bloquea los implementos por id, calcula el máximo compromiso simultáneo y asocia las cantidades mediante JDBC en la misma transacción. `EquipmentIntegrationTest` cubre nombres duplicados, permisos, actualización parcial, entradas numéricas inválidas, disponibilidad, reservas consecutivas, carreras de reserva y de edición, cancelación y comprobante desde una reserva real. El PDF se captura con el adaptador de correo de prueba, no se acredita entrega SMTP real desde ese flujo. `ReservationCreationIntegrationTest` y `ReservationCancellationIntegrationTest` cubren stock insuficiente y liberación conservando historial.

**Pendiente:** ampliar T012/T023 para cubrir exhaustivamente los contratos y T014 para solapes parciales y máximos simultáneos adicionales; revisar índices con datos representativos en T024. Los RNF de interfaz no se verificaron: el cliente React vive en otro repositorio y su verificación se excluyó de esta ejecución de backend.

**Verificación de backend (2026-11-16):** `.\mvnw.cmd clean verify` terminó con 225 pruebas, sin fallos ni omisiones. `EquipmentIntegrationTest` incluye 10 pruebas; el rechazo de decimales, cadenas numéricas y valores fuera de rango se comprobó para stock y cantidades.

## Dependencies & Execution Order

`T001 → T002..T006 → (T007..T016 y T017..T023 en paralelo cuando compartan la base) → T024/T025`.

La base de UC13 depende de UC01, UC03 y UC07-H1; la integración del comprobante además requiere UC11-H1. El esquema es compartido con UC10. La reserva inicial de UC03 funciona sin implementos; el soporte completo del contrato de reserva con implementos se entrega al integrar UC13. US1 y US2 pueden desarrollarse en paralelo tras la base de catálogo, pero las pruebas integradas requieren ambas.

## Notes

- `[US1]` y `[US2]` se pueden implementar por separado.
- La disponibilidad se deriva; no hay saldo almacenado (D-23).
- El límite de cantidad es el stock de la franja (OQ-18).

## Checklist de auto-revisión

- [ ] Alineado con SPEC 013 y `contracts/equipment/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Validación de stock dentro de la transacción.
- [ ] Canceladas liberan stock y comprobante lista implementos.
