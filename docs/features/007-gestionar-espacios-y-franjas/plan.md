# Implementation Plan: UC07 - Gestionar espacios y franjas

**Date**: 2026-11-10
**Spec**: [spec.md](spec.md)

## Summary

UC07 implementa la gestión de espacios y bloqueos [SPEC 007 RF-001..RF-011]. Los bloqueos se materializan en una fila por franja y cancelan reservas futuras sin descontar cuota; si afectan una reserva en curso se rechaza toda la operación.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas |
|---|---|---|
| RF-001, RF-002 | `SpaceService` | T007, T011 |
| RF-003 | franjas derivadas del horario | T007 |
| RF-004, RF-005 | estado y disciplinas del espacio | T007, T012 |
| RF-006, RF-007 | `BlockService` (puntual/recurrente) | T008 |
| RF-008, RF-009 | cancelación de reservas afectadas | T009 |
| RF-010 | motivo visible en el calendario (UC02) | T009 |
| RF-011 | reautenticación en bloqueos | T018 |
| RNF-001..RNF-003 | zona horaria, WCAG, auditoría | T013, T020 |
| CE-001..CE-006 | aceptación | T014, T021 |
| HU1, HU2 | espacios y bloqueos | Fase 3 |

## Technical Context

**Language/Version**: Java 25 [PLAN OQ-01].
**Primary Dependencies**: Spring Boot 4.2.0: Web MVC, Validation, Data JPA, Security; MapStruct [D-24].
**Storage**: PostgreSQL para entidades y referencias (`space`, `space_discipline`, `space_image`, `storage_object_cleanup` e `institutional_block`); Supabase Storage privado para los archivos de imagen.
**Messaging**: evento de aviso a afectados después del commit [D-10].
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers.
**Target Platform**: contenedores Docker.
**Project Type**: backend monolítico en n capas [D-02].
**Performance Goals**: operaciones administrativas poco frecuentes; consultas de disponibilidad eficientes.
**Constraints**: franjas de 60 min (RN-08); mantenimiento no reservable (RN-09); prioridad de bloqueos (RN-42); cancelación sin cuota (RN-43); reautenticación (D-21); America/Bogota (RNF-07).
**Scale/Scope**: 4 endpoints originales más 2 de imágenes, esquema en la migración única compartida, 11 RF + 3 RNF + 6 CE + 2 HU.

## Project Structure

### Documentation

```text
docs/features/007-gestionar-espacios-y-franjas/
├── plan.md
└── spec.md
docs/techincal-plan/contracts/spaces/   # 5 contratos, incluidos los de imágenes
docs/techincal-plan/contracts/blocks/   # 1 contrato
```

### Source Code

```text
src/main/java/unimagdeportes/
├── controller/space/SpaceController.java
├── controller/block/BlockController.java
├── service/space/SpaceService.java
├── service/space/SpaceImageValidator.java
├── storage/{ImageStorage,SupabaseImageStorage,StorageObjectCleanup}.java
├── service/block/BlockService.java
├── repository/{SpaceRepository,InstitutionalBlockRepository}.java
├── model/{Space,SpaceImage,InstitutionalBlock,SpaceStatus,BlockType}.java
├── dto/{space,block}/ · mapper/{space,block}/

src/main/resources/db/migration/
└── V1__shared_schema.sql

src/test/java/unimagdeportes/{contract/{spaces,blocks},integration/{spaces,blocks},unit/blocks}/
```

**Structure Decision**: espacios y bloqueos se separan en los módulos `space` y `block`; se comunican por sus services [PLAN §3.4].

## Reglas de negocio

1. **Atributos** [RN-06]: nombre, disciplinas, aforo, requisitos, horario y estado.
2. **Franjas** [RN-08]: bloques de 60 min dentro del horario; no se persisten.
3. **Mantenimiento** [RN-09]: no reservable.
4. **Espacio compartido** [RN-07]: la reserva bloquea el espacio completo.
5. **Bloqueos** [RN-41]: clases, eventos o mantenimiento; puntuales o recurrentes.
6. **Prioridad** [RN-42]: el bloqueo prevalece sobre la reserva libre.
7. **Cancelación** [RN-43]: se rechaza toda la operación si afecta una reserva en curso; las reservas futuras afectadas pasan a `CANCELLED_BY_ADMIN` sin descontar cuota y se avisa. Las reservas afectadas deben seguir siendo futuras en la validación inmediatamente anterior al commit; si alguna comenzó, la transacción se revierte íntegramente.
8. **Recurrencia** [OQ-06]: materializada por franja con `recurrence_group`; no se edita por grupo.
9. **Desactivar** [OQ-12]: equivale a `MAINTENANCE`; no cancela reservas existentes (OQ-05).

## Contrato REST

Fuente: [`contracts/spaces/`](../../techincal-plan/contracts/README.md) y [`contracts/blocks/`](../../techincal-plan/contracts/README.md). Convenciones: [`contracts/README.md`](../../techincal-plan/contracts/README.md).

## Estrategia de testing

- Contrato: espacios y bloqueos en T014/T021.
- Integración: creación/edición/mantenimiento, recurrencia, cascada y reautenticación en T014/T021.
- Unitario: materialización de recurrencia en T022.
- Aceptación: CE-001..CE-006 [SPEC 007].

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Verificar UC01 para US1; US2 requiere además UC03.

### Phase 2: Foundational

- [x] **T002** Crear `Space` y `SpaceStatus`.
- [x] **T003** Modelar las disciplinas como colección JPA de `Space` (representación acordada en lugar de una entidad `SpaceDiscipline` independiente).
- [x] **T004** Crear `InstitutionalBlock` y `BlockType`.
- [x] **T005** Verificar espacios, disciplinas y bloqueos en la migración única.
- [x] **T006** Crear los repositorios de espacio y bloqueo; las disciplinas se gestionan mediante la colección JPA de `Space`.
- [ ] **T007** Implementar `SpaceService`.
- [x] **T008** Implementar `BlockService` con materialización de recurrencia.
- [x] **T009** Adquirir primero el bloqueo asesor global de §4.1 del plan general; releer y validar reservas y hora, rechazar atómicamente si alguna está en curso y, si no, cancelar futuras y crear bloqueos en la misma transacción.

### Phase 3: User Stories

- [x] **T010** [US1/P1] Crear DTO y mapper de espacio.
- [x] **T011** [US1/P1] Implementar `GET/POST /admin/spaces` y `PUT /admin/spaces/{id}`.
- [x] **T012** [US1/P1] Validar horario, disciplinas y aforo.
- [x] **T013** [US1/P1] Registrar auditoría de espacios.
- [ ] **T014** [US1/P1] Pruebas de contrato e integración de espacios.
- [x] **T015** [US2/P1] Crear DTO y mapper de bloqueo.
- [x] **T016** [US2/P1] Implementar `POST /admin/blocks`.
- [x] **T017** [US2/P1] Publicar el evento de aviso a afectados.
- [x] **T018** [US2/P1] Exigir reautenticación (`REAUTH_REQUIRED`).
- [x] **T019** [US2/P1] Registrar auditoría de bloqueos y cancelaciones.
- [ ] **T020** [US2/P1] Pruebas de contrato de bloqueos.
- [ ] **T021** [US2/P1] Integración de recurrencia y cascada; probar carreras con reservas, check-in y cancelación, rechazo íntegro, rollback y ausencia de bloqueos mutuos.
- [x] **T022** [US2/P1] Prueba unitaria de materialización.
- [ ] **T023** [US1/P1] Verificar que "desactivar" equivale a mantenimiento.

### Phase 4: Polish

- [ ] **T024** Verificar que los cambios de espacio no cancelan reservas existentes.
- [ ] **T025** Revisar rendimiento de recurrencias largas y ejecutar `./mvnw clean verify`.

**Avances parciales:** `SpaceService` incorpora gestión administrativa, pero T007 sigue pendiente de integración con disponibilidad. Las pruebas de `BlockIntegrationTest` verifican la cascada puntual, el rechazo y el rollback antes del commit en PostgreSQL; las carreras y la integración de recurrencia de T021 siguen pendientes.

### Ampliación pendiente: imágenes de espacios

#### Cambio aprobado: Supabase Storage privado

**Implementado; aceptación remota pendiente:** se sustituyen los binarios de PostgreSQL por referencias a objetos. El proyecto indicado es `https://teiezrgnhzarhhsycwsh.supabase.co` y el bucket privado es `space-images` (datos proporcionados por el responsable del prototipo; no se han verificado con credenciales). No existen imágenes que migrar según el responsable.

- Se mantiene la carga multipart al backend, los permisos, las dos imágenes obligatorias y la consulta autenticada actual. El backend valida y sube los archivos automáticamente; el cliente no recibe credenciales de Supabase.
- Guardar en `space_image` únicamente UUID, clave estable del objeto, tipo detectado y tamaño. No guardar binarios ni URLs temporales. Adaptar la V1 editable conforme a la política de desarrollo.
- Separar validación, acceso a Storage y persistencia. Transferir los archivos sin copias completas innecesarias; limitar los píxeles decodificados mediante configuración, sin comprimir ni cambiar la calidad.
- Subir antes de la transacción de negocio, sin mantener el bloqueo global durante la transferencia. Confirmar ambas referencias atómicamente. En reemplazo, conservar el objeto anterior hasta confirmar la nueva referencia.
- Registrar limpieza persistente de objetos nuevos no confirmados y de objetos reemplazados; reintentar los borrados fallidos sin eliminar objetos referenciados.
- Mantener el bucket privado y entregar las imágenes mediante streaming por el backend. Configurar el acceso mediante variables de entorno y responder con un error controlado si Storage no está configurado o falla.
- Verificar con un servidor HTTP local y PostgreSQL los contratos de Storage, permisos, referencias sin binarios, rollback y limpieza. La aceptación contra el proyecto real queda pendiente de configurar la credencial exclusivamente en el servidor.

**Estado:** almacenamiento y endpoints adaptados a Supabase. La auditoría posterior ejecutó la suite completa de 277 pruebas sin fallos ni omisiones, construyó la imagen y verificó su arranque con PostgreSQL y Mailpit. Esa ejecución precede a las correcciones de auditoría descritas abajo y no acredita acceso al bucket real.

**Evidencia inicial:** primero pasaron 106 pruebas sin Docker; una ejecución de integración quedó bloqueada por Docker no disponible. Más adelante `.\mvnw.cmd clean verify` completó 277 pruebas y empaquetó el JAR, aunque Surefire tuvo que forzar el cierre de su JVM. No se presenta el bloqueo inicial como estado vigente.

**Correcciones posteriores a la auditoría:** el controller obtiene un DTO con stream desde `SpaceService`, sin invocar Storage directamente. Un fallo temprano de lectura retira tipo y tamaño de imagen y conserva CORS y cabeceras de seguridad. `SchedulingConfig` aísla la limpieza; subidas y borrados tienen plazo total menor que una hora, y la compensación se renueva o recrea al resolver la subida, también ante fallo. La validación decodifica una muestra acotada y no modifica los bytes originales. Se incorporaron regresiones de CORS con filtros reales, timeout de escritura, aislamiento de jobs, expiración de compensaciones, imagen de aproximadamente 15 MB y reemplazos concurrentes. La verificación de estas correcciones y la aceptación remota se registran por separado; no se deducen del resultado de 277 pruebas.

- Cada espacio requiere una imagen de vista previa y una imagen panorámica de 360° al crearse. Ambas pueden reemplazarse independientemente, pero no eliminarse.
- El Administrador proporciona ambas imágenes al crear el espacio y puede reemplazarlas mediante un endpoint específico. La consulta individual requiere autenticación.
- Los archivos se almacenan en Supabase Storage; `space_image` conserva clave estable, tipo y tamaño y `space` contiene dos claves foráneas obligatorias. El límite es 30 MiB (31.457.280 bytes) por archivo, 64 MiB por solicitud, 64.000.000 de píxeles y 16.384 por dimensión por defecto (configurables). La muestra de validación tiene hasta 1024 × 1024 píxeles y se descarta; el archivo original no se comprime ni redimensiona. El detector ImageIO reconoce el contenido según sus lectores disponibles y no garantiza reconocer todos los formatos existentes.
- La imagen de vista previa se destina a la reserva y la panorámica al espacio al reservar; esta ampliación cubre su almacenamiento y entrega, no la implementación de un visor 360.

**Tareas adicionales:**

- [x] **T026** Revalidar en PostgreSQL la tabla de referencias sin binarios, la cola de limpieza y las dos referencias obligatorias en espacio tras el cambio de Storage (suite de 277 pruebas).
- [ ] **T027** Verificar los contratos de creación con dos imágenes, reemplazo administrativo y consulta autenticada individual; no existe eliminación de imágenes.
- [ ] **T028** Verificar la detección por contenido, sus formatos soportados y el límite de 30 MiB por archivo y de solicitud multipart.
- [ ] **T029** Probar creación con ambas imágenes obligatorias, rechazo de imágenes ausentes, reemplazo independiente, acceso autenticado, permisos de Administrador, formatos, tamaño y ausencia de binarios en listados.

T026 precede a T027; T027–T028 preceden a T029. La integración con las vistas de disponibilidad y reserva consume las imágenes individualmente y no forma parte de la implementación de un visor 360.

**Evidencia histórica anterior a Supabase:** `.\mvnw.cmd clean verify` ejecutó 107 pruebas sin fallos ni pruebas omitidas, incluidas las pruebas citadas. Maven informó `BUILD SUCCESS`, aunque Surefire forzó el cierre de su JVM después del tiempo de espera de apagado. T014, T020–T021 y T027–T029 permanecen abiertos porque las pruebas existentes no cubren todos sus casos; T025 sigue abierto por la revisión de rendimiento.

## Dependencies & Execution Order

`T001 → T002/T003/T005/T006/T007 → T010..T014 → UC03 → T004/T008/T009/T015..T022 → T023..T025` (el esquema es compartido; la integración de bloqueo con reservas espera UC03).

UC07-US1 depende de UC01; US2 requiere además UC03. UC02 consume espacios y bloqueos.

## Notes

- `[US1]` y `[US2]` pueden implementarse por separado (US2 requiere US1).
- Los bloqueos son inmutables en el prototipo; crear otro no retira el anterior (OQ-06).
- El QR del espacio se implementa en UC05.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 007 y `contracts/spaces|blocks/`.
- [ ] RF/RNF/CE/HU trazados.
- [ ] Cascada de cancelación sin cuota y con aviso.
- [x] Reautenticación en bloqueos.
