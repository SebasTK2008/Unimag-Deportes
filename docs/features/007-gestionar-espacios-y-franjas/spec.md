# Especificación de Funcionalidad: UC07 - Gestionar espacios y franjas

**Creado**: 2026-10-13  

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
   - **Dado** que existen reservas en las franjas que se van a bloquear.
   - **Cuando** el Administrador crea el bloqueo.
   - **Entonces** esas reservas se cancelan automáticamente con estado "Cancelada por administrador", los Usuarios reciben un aviso con el motivo y las horas no cuentan contra su cuota.

### Casos Extremos (Edge Cases)

- **¿Qué disciplinas existen?**
  Baloncesto, voleibol, fútbol 5, fútbol 11 y tenis de campo (sección 1 del contexto).

- **¿Cómo se comporta un espacio que sirve para varias disciplinas?**
  La reserva bloquea el espacio completo, sin importar la disciplina elegida (RN-07).

- **¿Qué duración tienen las franjas?**
  60 minutos dentro del horario de operación de cada espacio (RN-08); la duración es un parámetro configurable (ver SPEC 006).

- **¿Qué ocurre con las reservas existentes al desactivar un espacio o ponerlo en mantenimiento?**
  **[Pendiente de definir]** El contexto no lo establece.

- **¿Qué diferencia hay entre el estado "en mantenimiento" de un espacio y un bloqueo institucional de tipo mantenimiento?**
  **[Pendiente de definir]** El contexto contempla ambos (RN-06, RN-09 y RN-41) pero no precisa cuándo se usa cada uno ni si un espacio en mantenimiento cancela las reservas existentes como lo hace un bloqueo (RN-43).

- **¿Qué tipos de bloqueo existen?**
  Clases y grupos formativos (fijos y recurrentes), eventos o torneos (puntuales) y mantenimiento (RN-41).

- **¿Qué prevalece entre un bloqueo y una reserva libre?**
  El bloqueo institucional (RN-42).

- **¿Cómo se ven las franjas bloqueadas?**
  Como no disponibles en el calendario, con el motivo visible (RN-44).

- **¿Se cobra cuota a los Usuarios cuyas reservas se cancelan por un bloqueo?**
  No. Las horas no cuentan contra su cuota (RN-43).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador crear, editar y desactivar espacios (RF-11).
- **RF-002**: El sistema DEBE registrar de cada espacio: nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado (activo o en mantenimiento) (RN-06, RF-11).
- **RF-003**: El sistema DEBE definir las franjas de cada espacio en bloques de 60 minutos dentro de su horario de operación (RN-08).
- **RF-004**: El sistema NO DEBE mostrar como reservable un espacio en mantenimiento (RN-09).
- **RF-005**: El sistema DEBE tratar un espacio compartido entre disciplinas como un único espacio completo para la reserva (RN-07).
- **RF-006**: El sistema DEBE permitir al Administrador crear bloqueos puntuales o recurrentes, con motivo, por clases y grupos formativos, eventos o torneos, o mantenimiento (RF-13, RN-41).
- **RF-007**: El sistema DEBE dar a los bloqueos institucionales prioridad sobre las reservas libres (RN-42).
- **RF-008**: El sistema DEBE cancelar automáticamente las reservas existentes en las franjas bloqueadas, con estado "Cancelada por administrador" (RN-43, RF-13).
- **RF-009**: El sistema DEBE avisar a los Usuarios afectados con el motivo del bloqueo y NO DEBE contar esas horas contra su cuota (RN-43, RN-47).
- **RF-010**: El sistema DEBE mostrar las franjas bloqueadas como no disponibles, con el motivo visible (RN-44).

### Requisitos No Funcionales

- **RNF-001**: La gestión DEBE cumplir WCAG (RNF-05 del contexto).
- **RNF-002**: Los horarios de operación y las fechas y horas de los bloqueos DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-003**: El sistema DEBE registrar auditoría de las cancelaciones generadas por el bloqueo (RNF-08 del contexto).

### Entidades Clave

- **Espacio**: Cancha o área física reservable (por ejemplo, "Cancha de tenis 1"). Atributos: nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado (activo o en mantenimiento).
- **Franja**: Bloque de 60 minutos reservable de un espacio, definido dentro de su horario de operación. No se modela como entidad independiente de gestión; se deriva del Espacio y de la duración configurada.
- **BloqueoInstitucional**: Franja o conjunto de franjas retiradas de la reserva libre. Atributos: espacio, fechas y horas bloqueadas, tipo (clases y grupos formativos, eventos o torneos, o mantenimiento), carácter puntual o recurrente, y motivo.
- **Reserva**: Entidad definida en la SPEC 003. Las reservas afectadas por un bloqueo pasan a "Cancelada por administrador".

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Integridad de Espacios, "100% de los espacios registrados contienen nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado".
- **CE-002**: Consistencia de Franjas, "0 franjas reservables fuera del horario de operación de su espacio o en un espacio en mantenimiento".
- **CE-003**: Prioridad Institucional, "0 reservas activas en franjas bloqueadas institucionalmente".
- **CE-004**: Equidad de Cuota, "100% de las reservas canceladas por un bloqueo no cuentan contra la cuota del Usuario".
- **CE-005**: Comunicación, "100% de los Usuarios con reservas afectadas reciben un aviso con el motivo".
