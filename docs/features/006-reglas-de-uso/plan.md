# Implementation Plan: UC06 - Reglas de uso

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC06 implementa la consulta de las reglas y la edición de cuota semanal y umbral de inasistencias por el Administrador [SPEC 006]. Solo estos dos valores viven en `usage_parameters`; los demás son fijos o se configuran por despliegue. Las acciones sensibles exigen reautenticación reciente [D-21].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002, RF-003 | `ParameterService`, `ParameterController` | T004, T005 |
| RF-004, RF-005 | edición de parámetros de negocio | T008, T011 |
| RF-006, RF-007 | aplicación y valores por defecto | T009 |
| RNF-001..RNF-003 | DTO, zona horaria, WCAG | T002, T006 |
| CE-001..CE-004 | aceptación | T007, T014 |
| HU1, HU2 | consulta y configuración | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL; tabla singleton `usage_parameters`.
**Messaging**: sin eventos.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: lectura rápida de parámetros vigentes.
**Constraints**: valores configurables, no fijos en el código (sección 4 del contexto); `PUT` con reautenticación (D-21); cambios rigen para operaciones nuevas (OQ-05); America/Bogota (RNF-07).
**Scale/Scope**: 3 endpoints, sin migraciones propias (`V5` es de UC01), 7 RF + 3 RNF + 4 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/006-reglas-de-uso/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/rules/   # 3 contratos
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/parameter/ParameterController.java
├── service/parameter/ParameterService.java
├── repository/UsageParametersRepository.java
├── model/UsageParameters.java
├── dto/parameter/ · mapper/parameter/ParameterMapper.java

src/test/java/unimagdeportes/{contract/rules,integration/rules,unit}/
```

**Structure Decision**: `UsageParameters` se creó en la fase Foundational de UC01 (`V5`); este UC solo la expone y edita.

## Reglas de negocio

1. **Fuente** [sección 4 del contexto]: cuota y umbral de inasistencias salen de `usage_parameters`; los demás valores son fijos o de despliegue.
2. **Consulta** [RF-09]: cuota, tolerancia, cancelación, check-in y sanciones en lenguaje claro.
3. **Edición** [RF-12]: solo Administrador con reautenticación reciente.
4. **Autenticación** [OQ-16, D-20]: los parámetros de acceso viven en `application.properties` y no se editan aquí.
5. **Efecto** [OQ-05]: los cambios rigen para operaciones nuevas; no modifican reservas existentes.
6. **Zona horaria** [RNF-07]: America/Bogota.

## Contrato REST

Fuente: [`contracts/rules/`](../../techincal-plan/contracts/README.md) (3 archivos). Convenciones y errores: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: `GET /rules`, `GET/PUT /admin/parameters` en T013.
- Integración: valores vigentes, reautenticación y alcance de los cambios en T007/T014/T015.
- Aceptación: CE-001..CE-004 [SPEC 006].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar prerrequisitos: UC01 (`SecurityConfig`, `ReauthPolicy`, `V5`).

### Phase 2: Foundational

- [ ] **T002** Crear los DTO de `dto/parameter/`.
- [ ] **T003** Crear `ParameterMapper`.

### Phase 3: User Stories

- [ ] **T004** [US1/P1] Implementar `ParameterService.getRules()`.
- [ ] **T005** [US1/P1] Implementar `GET /rules`.
- [ ] **T006** [US1/P1] Excluir los parámetros de autenticación de la respuesta.
- [ ] **T007** [US1/P1] Prueba de contrato e integración de la consulta.
- [ ] **T008** [US2/P1] Implementar `getParameters()` y `updateParameters()`.
- [ ] **T009** [US2/P1] Validar rangos de cada parámetro de negocio.
- [ ] **T010** [US2/P1] Exigir `REAUTH_REQUIRED` en el `PUT`.
- [ ] **T011** [US2/P1] Implementar `GET/PUT /admin/parameters`.
- [ ] **T012** [US2/P1] Registrar auditoría.
- [ ] **T013** [US2/P1] Pruebas de contrato de administración.
- [ ] **T014** [US2/P1] Integración de reautenticación.
- [ ] **T015** [US2/P1] Integración de "rige para operaciones nuevas".

### Phase 4: Polish

- [ ] **T016** Revisar valores por defecto contra la sección 4 del contexto.
- [ ] **T017** Verificar que los parámetros de autenticación nunca se exponen y ejecutar `./mvnw clean verify`.

## Dependencies & Execution Order

`T001 → T002/T003 → T004..T007 → T008..T015 → T016/T017`.

UC06 depende de UC01. Es prerrequisito de UC03, UC05, UC08 y UC10, que leen sus parámetros.

## Notes

- `[US1]` y `[US2]` permiten implementar consulta y edición por separado.
- La tabla es singleton (`id = 1`); cada guardado sobrescribe.
- No hay migración propia.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 006 y `contracts/rules/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Parámetros de autenticación fuera de la API.
- [ ] Reautenticación aplicada al `PUT`.
