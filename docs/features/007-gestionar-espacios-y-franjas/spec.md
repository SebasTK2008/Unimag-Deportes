# Especificación de Funcionalidad: UC07 - Gestionar espacios y franjas

**Creado**: 2026-10-13  
**Última revisión**: 2026-11-25 (nombres actuales de creadores y canceladores, D-28)

**Imágenes de espacios:** al crear un espacio se requieren una imagen de vista previa y una panorámica 360°. Cada una puede reemplazarse, pero no eliminarse. El almacenamiento y los contratos se describen en [plan.md](plan.md#ampliación-pendiente-imágenes-de-espacios).

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Gestionar los espacios y su horario de operación (Prioridad: P1)

Como Administrador, quiero crear, editar y desactivar espacios, definiendo su disciplina, aforo, requisitos, horario de operación y estado, de manera que las franjas reservables y su disponibilidad reflejen la realidad de cada espacio.

**Por qué esta prioridad**: Sin espacios y horarios de operación definidos no existen franjas que reservar; es la base de la plataforma.

**Prueba Independiente**: Crear un espacio con sus atributos, editar su horario y cambiar su estado a "en mantenimiento", y validar que las franjas se generan dentro del horario de operación y que el espacio en mantenimiento no aparece como reservable.

**Escenarios de Aceptación**:

1. **Escenario**: Creación de un espacio.
   - **Dado** que el Administrador está autenticado.
   - **Cuando** crea un espacio con nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado.
   - **Entonces** el espacio queda registrado y sus franjas de 60 minutos quedan definidas dentro de su horario de operación.

2. **Escenario**: Edición de un espacio.
   - **Dado** que existe un espacio.
   - **Cuando** el Administrador edita sus atributos, por ejemplo el horario de operación.
   - **Entonces** el sistema guarda los cambios y las franjas se ajustan a la nueva configuración.

3. **Escenario**: Espacio en mantenimiento.
   - **Dado** que el Administrador cambia el estado de un espacio a "en mantenimiento".
   - **Cuando** un Usuario consulta la disponibilidad.
   - **Entonces** el espacio no aparece como reservable.

---

### Historia de Usuario 2 - Bloquear franjas por clases, eventos o mantenimiento (Prioridad: P1)

Como Administrador, quiero bloquear franjas de un espacio por clases y grupos formativos, eventos o torneos, o mantenimiento, de manera que dejen de estar disponibles para la reserva libre y las reservas afectadas se cancelen automáticamente.

**Por qué esta prioridad**: Los bloqueos institucionales tienen prioridad sobre las reservas libres (RN-42) y su efecto sobre reservas existentes y sobre la cuota de los Usuarios debe ser correcto.

**Prueba Independiente**: Crear un bloqueo puntual y uno recurrente, uno de ellos sobre franjas con reservas existentes, y validar que las franjas quedan no disponibles con su motivo, que las reservas afectadas se cancelan, que los Usuarios son avisados y que sus horas no cuentan.

**Escenarios de Aceptación**:

1. **Escenario**: Bloqueo puntual sobre franjas libres.
   - **Dado** que el Administrador está autenticado y las franjas están libres.
   - **Cuando** crea un bloqueo puntual (por ejemplo, un torneo) con su motivo.
   - **Entonces** las franjas aparecen en el calendario como no disponibles, con el motivo visible.

2. **Escenario**: Bloqueo fijo y recurrente.
   - **Dado** que el Administrador define un bloqueo recurrente por clases o grupos formativos.
   - **Cuando** lo guarda.
   - **Entonces** las franjas correspondientes quedan bloqueadas de forma recurrente.

3. **Escenario**: Bloqueo sobre franjas con reservas existentes.
   - **Dado** que existen reservas futuras en las franjas que se van a bloquear y ninguna afectada está en curso.
   - **Cuando** el Administrador crea el bloqueo.
   - **Entonces** esas reservas se cancelan automáticamente con estado "Cancelada por administrador", los Usuarios reciben un aviso con el motivo y las horas no cuentan contra su cuota.

4. **Escenario**: Bloqueo que afecta una reserva en curso.
   - **Dado** que una reserva afectada está en curso.
   - **Cuando** el Administrador intenta crear el bloqueo.
   - **Entonces** se rechaza toda la operación, sin crear franjas ni cancelar reservas.

### Historia de Usuario 3 - Cancelar un bloqueo institucional (Prioridad: P1)

Como Administrador, quiero cancelar una franja institucional que ya no se necesita, conservando sus datos originales y la auditoría, para retirar ese impedimento sin reactivar reservas canceladas.

**Prueba Independiente**: crear un bloqueo sobre una reserva futura, cancelar su fila y consultar historial, disponibilidad y reserva original; intentar una reserva nueva.

**Escenarios de Aceptación**:

1. Cualquier ADMIN con reautenticación reciente cancela por ID una fila futura o en curso con motivo obligatorio (1–1000 caracteres sin espacios exteriores). Se conservan tipo, motivo original, intervalo y grupo; se registra actor, fecha y motivo separado.
2. USER no puede consultar el historial administrativo ni cancelar; ADMIN sin reautenticación puede leer, pero no cancelar.
3. La reserva anterior sigue `CANCELLED_BY_ADMIN`, sin compensaciones adicionales de cuota o implementos. La nueva reserva debe pasar las reglas ordinarias.
4. Otros bloqueos, mantenimiento, ocupación o reglas temporales pueden mantener la indisponibilidad. Cancelar un bloqueo de mantenimiento no cambia `SpaceStatus`.
5. Cancelar una fila no afecta sus hermanas ni la recurrencia. Una creación de varias horas requiere tratar cada fila individualmente.
6. Repetir con cuerpo válido conserva primera fecha, actor y motivo, incluso con otro motivo o después del fin; no duplica auditoría ni SSE. Una primera cancelación al llegar a `endAt` se rechaza.
7. Transición y auditoría son atómicas bajo `ReservationLock`; no se publica éxito tras rollback. El listado paginado conserva cancelados; las lecturas operativas los excluyen.
8. El listado y la respuesta de cancelación incluyen el creador identificado por `BLOCK_CREATED` y los nombres actuales de creador/cancelador. En la tarjeta se muestran «Creado por» y «Cancelado por», sin UUID visibles; los IDs se conservan para uso interno.
9. Auditoría ausente, con actor nulo o contradictoria no atribuye creador. Usuario ausente o nombre vacío da nombre `null`; el cliente muestra «Administrador no disponible». En activos no muestra cancelador. El cambio de rol no borra autoría; el cambio de perfil actualiza el nombre mostrado, sin cambiar auditorías ni primera cancelación.

### Casos Extremos (Edge Cases)

- **¿Qué disciplinas existen?**
  Baloncesto, voleibol, fútbol 5, fútbol 11 y tenis de campo (sección 1 del contexto).

- **¿Cómo se comporta un espacio que sirve para varias disciplinas?**
   La reserva bloquea el espacio completo, sin importar la disciplina elegida (RN-07).

- **¿Qué combinación de disciplinas es incompatible?**
  Fútbol 5 y Fútbol 11 no pueden coexistir en el mismo espacio, al crear ni al editar.
  La API devuelve `400 VALIDATION_ERROR` en `details.fields.disciplines`; el formulario
  debe impedir seleccionar la combinación. Las demás combinaciones se mantienen permitidas.

- **¿Qué duración tienen las franjas?**
  60 minutos fijos dentro del horario de operación de cada espacio (RN-08).

- **¿Qué ocurre con las reservas existentes al desactivar un espacio o ponerlo en mantenimiento?**
  Los cambios rigen solo para operaciones nuevas: las reservas existentes se mantienen y no se cancelan. "Desactivar" un espacio equivale a pasarlo a mantenimiento (OQ-05, OQ-12).

- **¿Qué diferencia hay entre el estado "en mantenimiento" de un espacio y un bloqueo institucional de tipo mantenimiento?**
  Poner el espacio en mantenimiento lo oculta de la reserva futura sin cancelar reservas existentes (OQ-05). Un bloqueo institucional de tipo mantenimiento cancela las reservas futuras afectadas; si afecta una en curso se rechaza toda la operación (RN-43).

- **¿Qué tipos de bloqueo existen?**
  Clases y grupos formativos (fijos y recurrentes), eventos o torneos (puntuales) y mantenimiento (RN-41). Todos se materializan en una fila por franja; los recurrentes se agrupan por `recurrence_group` (OQ-06). D-27 permite cancelar lógicamente una fila conservando sus datos e historial. No se permite editar los datos originales, borrar físicamente, sustituir ni cancelar masivamente una serie; crear otro bloqueo no retira el anterior.

- **¿Qué prevalece entre un bloqueo y una reserva libre?**
  El bloqueo institucional (RN-42).

- **¿Cómo se ven las franjas bloqueadas?**
  Como no disponibles en el calendario, con el motivo visible (RN-44).

- **¿Se cobra cuota a los Usuarios cuyas reservas se cancelan por un bloqueo?**
  No. Las horas no cuentan contra su cuota (RN-43).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador crear, editar y desactivar espacios; desactivar equivale a pasar a mantenimiento (RF-11, OQ-12).
- **RF-002**: El sistema DEBE registrar de cada espacio: nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado (activo o en mantenimiento) (RN-06, RF-11).
- **RF-003**: El sistema DEBE definir las franjas de cada espacio en bloques de 60 minutos dentro de su horario de operación (RN-08).
- **RF-004**: El sistema NO DEBE mostrar como reservable un espacio en mantenimiento (RN-09).
- **RF-005**: El sistema DEBE tratar un espacio compartido entre disciplinas como un único espacio completo para la reserva (RN-07).
- **RF-006**: El sistema DEBE permitir al Administrador crear bloqueos puntuales o recurrentes, con motivo, por clases y grupos formativos, eventos o torneos, o mantenimiento (RF-13, RN-41).
- **RF-007**: El sistema DEBE dar a los bloqueos institucionales no cancelados prioridad sobre las reservas libres (RN-42).
- **RF-008**: El sistema DEBE rechazar atómicamente todo bloqueo que afecte una reserva en curso; en otro caso DEBE cancelar las reservas futuras afectadas con estado "Cancelada por administrador" (RN-43, RF-13).
- **RF-009**: El sistema DEBE avisar a los Usuarios afectados con el motivo del bloqueo y NO DEBE contar esas horas contra su cuota (RN-43, RN-47).
- **RF-010**: El sistema DEBE mostrar las franjas con bloqueo no cancelado como no disponibles, con el motivo visible (RN-44).
- **RF-011**: El sistema DEBE exigir reautenticación reciente del Administrador para crear y cancelar bloqueos institucionales (D-21, D-27).
- **RF-012**: El sistema DEBE permitir a cualquier ADMIN cancelar individualmente un bloqueo futuro o en curso con motivo, conservando datos originales, actor y fecha; sin borrado físico ni propagación a la recurrencia.
- **RF-013**: El sistema DEBE ofrecer consulta administrativa paginada por espacio, rango y estado, incluido el historial de cancelados, sin reautenticación de lectura.
- **RF-014**: El sistema NO DEBE reactivar ni modificar reservas, cuotas o implementos al cancelar un bloqueo. Solo los no cancelados imponen indisponibilidad.
- **RF-015**: El sistema DEBE cancelar y auditar atómicamente; los reintentos válidos conservan la primera cancelación sin otra transición ni SSE. No se envían correos de levantamiento.
- **RF-016**: La API administrativa DEBE añadir `createdBy`, `createdByName` y `cancelledByName` al listado y cancelación, sin eliminar IDs existentes ni exponer correos; resolver por lotes sin N+1, desde auditoría de creación y perfiles actuales, sin migración ni invención de autoría (D-28). Los nombres pueden cambiar entre reintentos; actor, fecha y motivo persistidos no.

### Requisitos No Funcionales

- **RNF-001**: La gestión DEBE cumplir WCAG (RNF-05 del contexto).
- **RNF-002**: Los horarios de operación y las fechas y horas de los bloqueos DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-003**: El sistema DEBE registrar auditoría de creación y cancelación de bloqueos y, separadamente, de las cancelaciones de reservas generadas al crearlos (RNF-08 del contexto).

### Entidades Clave

- **Espacio**: Cancha o área física reservable (por ejemplo, "Cancha de tenis 1"). Atributos: nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado (activo o en mantenimiento).
- **Franja**: Bloque de 60 minutos reservable de un espacio, definido dentro de su horario de operación. No se modela como entidad independiente de gestión; se deriva del Espacio y de la duración configurada.
- **BloqueoInstitucional**: Una fila por franja retirada de reserva libre: espacio, intervalo, tipo, recurrencia y motivo original. Estado `ACTIVE`/`CANCELLED`; `cancelledAt`, `cancelledBy` y `cancellationReason` nulos en activo y obligatorios en cancelado. Activo significa no cancelado, no necesariamente futuro.
- **Reserva**: Entidad definida en la SPEC 003. Las reservas afectadas por un bloqueo pasan a "Cancelada por administrador".

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Integridad de Espacios, "100% de los espacios registrados contienen nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado".
- **CE-002**: Consistencia de Franjas, "0 franjas reservables fuera del horario de operación de su espacio o en un espacio en mantenimiento".
- **CE-003**: Prioridad Institucional, "0 reservas activas en franjas con bloqueo institucional no cancelado".
- **CE-004**: Equidad de Cuota, "100% de las reservas canceladas por un bloqueo no cuentan contra la cuota del Usuario".
- **CE-005**: Comunicación, "100% de los Usuarios con reservas afectadas reciben un aviso con el motivo".
- **CE-006**: Seguridad, "0 bloqueos institucionales creados o cancelados sin reautenticación reciente del Administrador".
- **CE-007**: Historial, "100% de las cancelaciones conservan datos originales y una auditoría de transición con actor, fecha y motivo".
- **CE-008**: No reactivación, "0 reservas previas modificadas al cancelar un bloqueo; 0 auditorías de transición duplicadas por reintentos".
- **CE-009**: Autoría legible, "100% de los nombres disponibles provienen del perfil del actor correcto; 0 UUID como sustituto visible de nombres en tarjetas". La aceptación visual requiere React; el backend verifica campos, nulabilidad y consultas por lotes.
