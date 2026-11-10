# Implementation Plan: UC05 - Confirmar asistencia (QR)

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC05 implementa el check-in escaneando el QR estático del espacio y la generación/impresión de esos QR por el Administrador [SPEC 005 RF-001..RF-008]. El QR solo identifica el espacio; la identidad y la reserva salen del token y de la base de datos. Se respetan la ventana de check-in y los motivos de rechazo.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `CheckInService` | T004 |
| RF-003 | flujo de login desde el cliente + filtro | T004 (backend) |
| RF-004 | motivos de rechazo | T007 |
| RF-005 | `CheckInWindowPolicy` | T002 |
| RF-006, RF-007, RF-008 | `QrService` + `SpaceController` | T014, T015 |
| RNF-001..RNF-004 | DTO, auditoría, zona horaria, WCAG | T005, T008 |
| CE-001..CE-004 | aceptación | T009..T011, T017 |
| HU1, HU2 | check-in y QR | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security. QR: `TODO: elegir librería` (se decide en implementación) [PLAN §Technical Context].
**Storage**: PostgreSQL; `reservation.checked_in_at` y lectura de `space`.
**Messaging**: sin eventos propios; el check-in habilita el paso a `COMPLETED` en UC10.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: check-in en pocos segundos desde el celular.
**Constraints**: ventana de 10 min antes al fin de tolerancia (RN-36); tolerancia con 10 min desde la creación (RN-30); identidad del token (RN-32); QR estático (RN-31); motivos visibles (RN-35); America/Bogota (RNF-07).
**Scale/Scope**: 2 endpoints, sin migraciones, 8 RF + 4 RNF + 4 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/005-confirmar-asistencia-qr/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/checkin/   # 2 contratos
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/checkin/CheckInController.java
├── controller/space/SpaceController.java
├── service/checkin/{CheckInService,QrService}.java
├── service/policy/CheckInWindowPolicy.java
├── repository/{ReservationRepository,SpaceRepository}.java
└── dto/checkin/ · mapper/checkin/CheckInMapper.java

src/test/java/unimagdeportes/{contract/checkin,integration/checkin,unit/policy}/
```

**Structure Decision**: el check-in vive en `checkin`; el QR se expone desde `SpaceController` con rol Administrador [PLAN §3.5].

## Reglas de negocio

1. **Ventana** [RN-36]: desde 10 min antes del inicio hasta el fin de la tolerancia.
2. **Reserva en tolerancia** [RN-30]: check-in dentro de 10 min desde su creación.
3. **Identidad** [RN-31, RN-32]: QR estático por espacio; identidad del token.
4. **Dueño** [RN-33]: solo el usuario de la reserva puede hacer check-in.
5. **Sesión** [RN-34]: sin token o en otro navegador, se inicia sesión y se vuelve al check-in.
6. **Rechazos** [RN-35]: sin reserva, demasiado temprano, ventana cerrada o reserva cancelada.
7. **Efecto** [RN-24, RN-37]: `IN_USE`; sin check-in al cerrar, pasa a inasistencia (UC10).
8. **QR** [RF-16]: enlace fijo `/checkin/{id_espacio}`, sin datos del usuario.

## Contrato REST

Fuente: [`contracts/checkin/`](../../techincal-plan/contracts/README.md) (2 archivos). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: check-in y QR en T009/T017.
- Integración: ventana, reserva ajena, cancelada o inexistente en T010/T011.
- Unitario: `CheckInWindowPolicy` en T012.
- Aceptación: CE-001..CE-004 [SPEC 005].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01, UC03 y UC07-H1.

### Phase 2: Foundational

- [ ] **T002** Implementar `CheckInWindowPolicy`.
- [ ] **T003** Agregar la consulta de reserva activa por espacio y usuario.

### Phase 3: User Stories

- [ ] **T004** [US1/P1] Implementar `CheckInService`.
- [ ] **T005** [US1/P1] Crear DTO y `CheckInMapper`.
- [ ] **T006** [US1/P1] Implementar `POST /checkin/{spaceId}`.
- [ ] **T007** [US1/P1] Devolver los motivos de rechazo.
- [ ] **T008** [US1/P1] Registrar auditoría del check-in.
- [ ] **T009** [US1/P1] Prueba de contrato del check-in.
- [ ] **T010** [US1/P1] Integración de la ventana.
- [ ] **T011** [US1/P1] Integración de rechazos.
- [ ] **T012** [US1/P1] Prueba unitaria de `CheckInWindowPolicy`.
- [ ] **T013** [US2/P1] Elegir y documentar la librería de QR.
- [ ] **T014** [US2/P1] Implementar `QrService`.
- [ ] **T015** [US2/P1] Implementar `GET /admin/spaces/{id}/qr`.
- [ ] **T016** [US2/P1] Restringir el endpoint al rol `ADMIN`.
- [ ] **T017** [US2/P1] Pruebas de contrato y de contenido del QR.

### Phase 4: Polish

- [ ] **T018** Revisar el flujo de login al abrir el QR en otro navegador.
- [ ] **T019** Revisar legibilidad del QR impreso y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001 → T002/T003 → T004..T012 → T013..T017 → T018/T019`.

UC05 depende de UC01, UC03 y UC07-H1. US1 y US2 pueden implementarse en paralelo (US2 solo necesita espacios).

## Notes

- `[US1]` y `[US2]` son independientes.
- El QR no identifica al usuario (RN-32).
- El flujo de login al escanear lo resuelve el cliente con UC01; el backend solo valida el token.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 005 y `contracts/checkin/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Ventana y motivos de rechazo cubiertos.
- [ ] QR sin datos del usuario.
