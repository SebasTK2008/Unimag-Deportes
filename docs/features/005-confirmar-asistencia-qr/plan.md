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
**Primary Dependencies**: Spring Boot 4.2.0: Web MVC, Validation, Data JPA, Security. QR: `com.google.zxing:core:3.5.4` para generar la matriz y serialización SVG; decodificación y legibilidad pendientes de verificación integral.
**Storage**: PostgreSQL; `reservation.checked_in_at` y lectura de `space`.
**Messaging**: sin eventos propios; el check-in habilita el paso a `COMPLETED` en UC10.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: check-in en pocos segundos desde el celular.
**Constraints**: apertura configurada antes del inicio hasta una hora después del fin, inclusive (RN-30, RN-36); identidad del token (RN-32); QR estático (RN-31); motivos visibles (RN-35); America/Bogota (RNF-07).
**Scale/Scope**: 2 endpoints, sin migraciones, 8 RF + 4 RNF + 4 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/005-confirmar-asistencia-qr/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/checkin/   # 4 contratos
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/checkin/CheckInController.java
├── controller/space/SpaceController.java
├── service/checkin/{CheckInService,QrService}.java
├── service/policy/CheckInWindowPolicy.java
├── service/{reservation,space}/  # acceso mediante servicios propietarios
└── dto/checkin/ · mapper/checkin/CheckInMapper.java

src/test/java/unimagdeportes/{integration/checkin,service/checkin,service/policy}/
```

**Structure Decision**: el check-in vive en `checkin`; el QR se expone desde `SpaceController` con rol Administrador [PLAN §3.5].

## Reglas de negocio

1. **Ventana** [RN-36]: desde la apertura configurada antes del inicio hasta una hora después del fin, incluido el instante exacto, según el reloj del servidor.
2. **Reserva en tolerancia** [RN-30]: sigue la misma ventana; no hay plazo especial.
3. **Identidad** [RN-31, RN-32]: QR estático por espacio; identidad del token.
4. **Dueño** [RN-33]: solo el usuario de la reserva puede hacer check-in.
5. **Sesión** [RN-34]: sin token o en otro navegador, se inicia sesión y se vuelve al check-in.
6. **Rechazos** [RN-35]: sin reserva, demasiado temprano, ventana cerrada o reserva cancelada.
7. **Efecto** [RN-24, RN-37]: `IN_USE` antes del fin; desde el fin se registra asistencia y queda `COMPLETED`. La ocupación actual por otro usuario no impide confirmar una reserva propia ya terminada. Sin check-in, solo después del cierre puede pasar a inasistencia (UC10).
8. **QR** [RF-16]: enlace fijo `/checkin/{id_espacio}`, sin datos del usuario.

## Contrato REST

Fuente: [`contracts/checkin/`](../../techincal-plan/contracts/README.md) (4 archivos). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

- `GET /checkin/{spaceId}/reservations`: lista ordenada de reservas propias pendientes dentro de su ventana.
- `POST /checkin/{spaceId}/reservations/{reservationId}`: confirma la elegida, revalida bajo bloqueo y conserva idempotencia para `IN_USE`/`COMPLETED` con asistencia.
- Se conserva `POST /checkin/{spaceId}`; con varias pendientes elegibles devuelve `CHECKIN_SELECTION_REQUIRED`. El QR y su enlace no cambian.
- El cliente externo debe implementar la selección; el backend no acredita ese flujo visual.

## Estrategia de testing

- Contrato: check-in y QR en T009/T017.
- Integración: ventana, reserva ajena, cancelada o inexistente en T010/T011.
- Unitario: `CheckInWindowPolicy` en T012.
- Aceptación: CE-001..CE-004 [SPEC 005].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01, UC03 y UC07-H1.

### Phase 2: Foundational

- [x] **T002** Implementar `CheckInWindowPolicy`.
- [x] **T003** Agregar la consulta de reserva activa por espacio y usuario.

### Phase 3: User Stories

- [x] **T004** [US1/P1] Implementar `CheckInService`: adquirir primero el bloqueo asesor global de §4.1 del plan general y releer reserva y hora antes de confirmar.
- [x] **T005** [US1/P1] Crear DTO y `CheckInMapper`.
- [x] **T006** [US1/P1] Implementar `POST /checkin/{spaceId}`.
- [x] **T007** [US1/P1] Devolver los motivos de rechazo.
- [x] **T008** [US1/P1] Registrar auditoría del check-in.
- [ ] **T009** [US1/P1] Prueba de contrato del check-in.
- [ ] **T010** [US1/P1] Integración de la ventana.
- [ ] **T011** [US1/P1] Integración de rechazos.
- [x] **T012** [US1/P1] Prueba unitaria de `CheckInWindowPolicy`.
- [ ] **T013** [US2/P1] Agregar ZXing y verificar generación del QR en SVG.
- [x] **T014** [US2/P1] Implementar `QrService`.
- [x] **T015** [US2/P1] Implementar `GET /admin/spaces/{id}/qr`.
- [x] **T016** [US2/P1] Restringir el endpoint al rol `ADMIN`.
- [ ] **T017** [US2/P1] Pruebas de contrato y de contenido del QR.

### Phase 4: Polish

- [ ] **T018** Revisar el flujo de login al abrir el QR en otro navegador.
- [ ] **T019** Revisar legibilidad del QR impreso y ejecutar `./mvnw clean verify`.

**Estado de verificación:** las tareas de implementación marcadas están presentes en el código; esto no acredita la aceptación integral. La aprobación anterior de T012, la configuración del origen QR y `CheckInIntegrationTest` con PostgreSQL corresponde a una ejecución histórica, no a la validación de la ampliación del plazo. Existe una prueba de check-in desde una reserva real creada dentro de tolerancia y se añadieron casos de selección, propiedad, asistencia tardía, cierre exacto y concurrencia con inasistencias. La primera ejecución de `clean verify` de esta ampliación quedó bloqueada por Docker no accesible; una ejecución posterior sin contenedores aprobó 43 pruebas unitarias seleccionadas, sin acreditar las integraciones. El resultado vigente de la verificación completa se registra en la [guía de conexión](../../techincal-plan/backend-connection.md#6-verificación-automatizada). T013 tiene la dependencia, la generación SVG y una prueba automatizada de decodificación; la legibilidad impresa, T018, la experiencia móvil y WCAG requieren el cliente externo y comprobaciones manuales.

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
