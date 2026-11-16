# Implementation Plan: UC10 - Aplicar sanciones

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC10 marca la inasistencia al finalizar la reserva sin check-in y evalúa el umbral vigente sobre el mes calendario. La suspensión cancela las reservas futuras durante su período, libera cuota e implementos y notifica. No hay endpoint: se ejecuta por scheduler.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001 | `SanctionService.markNoShow` | T004 |
| RF-002 | `SanctionService.evaluateSuspension` | T010 |
| RF-003 | evento de notificación (UC11) | T005, T013 |
| RF-004 | reservas futuras y bloqueo de nuevas | T012 |
| RF-005 | parámetros de suspensión (UC06) | T010 |
| RF-006 | mes calendario y umbral vigente (OQ-07) | T011 |
| RNF-001..RNF-002 | auditoría y zona horaria | T007 |
| CE-001..CE-003 | aceptación | T008, T014, T015 |
| HU1, HU2 | inasistencia y suspensión | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Data JPA, Scheduling, Mail; MapStruct [D-24].
**Storage**: PostgreSQL; actualización de `reservation.status` y `app_user.suspended_until`.
**Messaging**: `ReservationNotificationEvent` (`NO_SHOW`, `SUSPENSION`, `CANCELLATION`) y `AvailabilityChangedEvent` después del commit [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers; `Clock` inyectado para simular tiempo.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico; job interno [D-07].
**Performance Goals**: job idempotente cada minuto sobre volumen bajo.
**Constraints**: sin check-in → `NO_SHOW` con horas contadas; mes calendario y umbral vigente; reservas futuras durante la suspensión se cancelan y liberan recursos; aviso; America/Bogota.
**Scale/Scope**: sin endpoints; marca mensual en la migración única compartida; 6 RF + 2 RNF + 3 CE + 2 HU.

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
├── service/policy/SanctionPolicy.java
├── service/{reservation,user}/  # servicios propietarios; ReservationService completa las reservas en uso
├── repository/sanction/MonthlySuspensionRepository.java
├── model/reservation/{Reservation,ReservationStatus}.java
└── event/{ReservationNotificationEvent,AvailabilityChangedEvent}.java   # reutilizados

src/test/java/unimagdeportes/{integration/sanctions,service/policy,scheduler}/
```

**Structure Decision**: el job solo orquesta; la lógica vive en `SanctionService`. La migración única compartida incluye `monthly_suspension(user_id UUID NOT NULL REFERENCES app_user(id), absence_month DATE NOT NULL, created_at timestamptz NOT NULL, PRIMARY KEY (user_id, absence_month), CHECK (EXTRACT(DAY FROM absence_month) = 1))`; el mes corresponde al primer día del mes local del fin programado.

## Reglas de negocio

1. **Inasistencia** [RN-37]: sin check-in al cerrar la ventana → `NO_SHOW`, horas contadas y aviso.
2. **Suspensión** [RN-38]: umbral vigente al registrar la falta (3 por defecto); duración configurada (7 días por defecto).
3. **Conteo** [OQ-07]: por mes calendario en America/Bogota.
4. **Reinicio** [OQ-07]: comienza un nuevo conteo el día 1.
5. **Reservas futuras** [RN-40]: se cancelan las que ocurren durante la suspensión, liberando cuota e implementos y notificando; no puede crear nuevas durante ella.
6. **Aviso** [RN-39]: correo con la fecha de reactivación.
7. **Idempotencia** [D-07]: el job puede reintentarse sin duplicar estados.

### Decisiones de implementación

- **Eventos**: se reutiliza `ReservationNotificationEvent` (`NO_SHOW`, `SUSPENSION`) en lugar de un `SanctionAppliedEvent` propio, porque `NotificationService` ya tiene esas plantillas.
- **Inicio de la suspensión**: dura `APP_RULES_SUSPENSION_DAYS` (7 por defecto) contados desde que el job registra la falta.
- **Suspensión vigente**: una suspensión nueva nunca acorta una vigente; se conserva la fecha más lejana.
- **Reservas canceladas por la suspensión**: cada una envía un correo `CANCELLATION` con la fecha y el motivo, y publica `AvailabilityChangedEvent`. Se consideran contenidas las que empiezan después del momento de la suspensión y terminan, como tarde, en su fecha de fin.
- **Auditoría**: las acciones del sistema se registran sin actor (`user_id` nulo) con los códigos `NO_SHOW_REGISTERED`, `USER_SUSPENDED` y `RESERVATION_CANCELLED_BY_SUSPENSION`.
- **Bloqueo**: cada reserva se procesa en su propia transacción; se adquiere el bloqueo asesor global, se relee la reserva y se bloquea la fila del usuario antes de modificarla.
- **Programación**: `APP_JOBS_RESERVATION_LIFECYCLE_CRON` (por defecto, cada minuto); las pruebas lo desactivan con `-` e invocan el job directamente.

## Contrato REST

No aplica: UC10 es interno (scheduler), sin endpoints.

## Estrategia de testing

- Unitario: `SanctionPolicy` (mes local, umbral y fin de la suspensión) y aislamiento de fallos del job.
- Integración: inasistencia, tolerancia e idempotencia en T008.
- Integración: suspensión, ventana y reservas futuras en T014/T015.
- Aceptación: CE-001..CE-003 [SPEC 010].

## Implementation Phases

### Phase 1: Setup

- [x] **T001** Verificar UC01, UC03, UC05, la base de UC11-H2 y el esquema compartido.
- [x] **T002** Agregar la consulta de reservas `CONFIRMED` con ventana cerrada.
- [x] **T003** Agregar la consulta de inasistencias del mes calendario.

**Base disponible:** la migración única crea `monthly_suspension` con clave por usuario y mes; `MonthlySuspensionRepository` la escribe con `on conflict do nothing`.

### Phase 2: Foundational

- [x] **T004** Implementar `SanctionService.markNoShow()`.
- [x] **T005** Publicar los avisos con `ReservationNotificationEvent` (`NO_SHOW`).
- [x] **T006** Implementar `ReservationLifecycleJob` idempotente: adquirir el bloqueo asesor global de §4.1 del plan general, releer estado y hora, marcar `NO_SHOW` o pasar `IN_USE` a `COMPLETED` al finalizar.
- [x] **T007** Registrar auditoría de la inasistencia.

### Phase 3: User Stories

- [x] **T008** [US1/P1] Integración de inasistencia al cerrar la ventana.
- [x] **T009** [US1/P1] Integración de reserva en tolerancia, transición `IN_USE` → `COMPLETED` al finalizar e idempotencia.
- [x] **T010** [US2/P2] Implementar `evaluateSuspension()` con umbral vigente y duración configurada.
- [x] **T011** [US2/P2] Calcular el conteo por mes calendario y probar el cambio de mes; persistir la marca única por usuario y mes en el esquema compartido y probar reintentos concurrentes.
- [x] **T012** [US2/P2] Actualizar `suspended_until`, auditar y cancelar reservas futuras durante la suspensión, liberando recursos y notificando.
- [x] **T013** [US2/P2] Publicar el aviso de suspensión.
- [x] **T014** [US2/P2] Integración de suspensión.
- [x] **T015** [US2/P2] Integración de cambio de mes, cambio de umbral, segunda falta tras suspensión y reservas parcialmente solapadas, verificando liberación y aviso.

### Phase 4: Polish

- [x] **T016** Revisar logs y métricas del job.
- [x] **T017** Verificar idempotencia con ejecuciones simultáneas y ejecutar `./mvnw clean verify`.

**Avance:** `ReservationLifecycleIntegrationTest` cubre:

- inasistencia al cerrar la ventana, con las horas contadas;
- reserva hecha dentro de la tolerancia y paso de `IN_USE` a `COMPLETED`, incluido el check-in en el último instante;
- idempotencia, y reintentos concurrentes de `markNoShow` que dejan una sola inasistencia, una suspensión y una marca mensual;
- suspensión por umbral, con reactivación en el correo;
- cancelación solo de las reservas contenidas, con liberación de cuota, espacio e implementos y aviso de disponibilidad;
- una suspensión por mes, reinicio del conteo tras una suspensión del mes anterior y límite del mes en America/Bogota;
- umbral vigente al registrar la falta;
- fallo de correo sin reversión.

`./mvnw clean verify` terminó con 190 pruebas sin fallos. T016 cubre los logs: el job registra los errores por reserva y un resumen por ejecución cuando procesa reservas. El proyecto no tiene infraestructura de métricas, así que no se añadieron. No hay prueba de reversión de la suspensión si falla una cancelación intermedia: depende de la transacción única de `markNoShow`.

## Dependencies & Execution Order

`T001 → T002/T003 → T004..T007 → T008/T009 → T010..T015 → T016/T017`.

UC10 depende de UC01, UC03, UC05 y la base de UC11-H2. UC09 consume el indicador de suspendidos.

## Notes

- `[US1]` y `[US2]` son secuenciales: la suspensión necesita inasistencias registradas.
- No hay endpoint propio.
- El conteo es por mes calendario y utiliza el umbral vigente al registrar la inasistencia (OQ-07).

## Checklist de auto-revisión

- [x] Alineado con SPEC 010.
- [x] RF/RNF/CE/HU trazados.
- [x] Job idempotente y con `Clock` inyectado.
- [x] Conteo por mes calendario y umbral vigente al registrar cada inasistencia.
