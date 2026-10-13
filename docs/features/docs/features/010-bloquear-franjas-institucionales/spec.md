# Especificación de Funcionalidad: UC10 - Bloquear franjas institucionales

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Bloquear franjas por clases, eventos o mantenimiento (Prioridad: P1)

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

- **RF-001**: El sistema DEBE permitir al Administrador crear bloqueos puntuales o recurrentes, con motivo, por clases y grupos formativos, eventos o torneos, o mantenimiento (RF-13, RN-41).
- **RF-002**: El sistema DEBE dar a los bloqueos institucionales prioridad sobre las reservas libres (RN-42).
- **RF-003**: El sistema DEBE cancelar automáticamente las reservas existentes en las franjas bloqueadas, con estado "Cancelada por administrador" (RN-43, RF-13).
- **RF-004**: El sistema DEBE avisar a los Usuarios afectados con el motivo del bloqueo y NO DEBE contar esas horas contra su cuota (RN-43, RN-47).
- **RF-005**: El sistema DEBE mostrar las franjas bloqueadas como no disponibles, con el motivo visible (RN-44).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE registrar auditoría de las cancelaciones generadas por el bloqueo (RNF-08 del contexto).
- **RNF-002**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **BloqueoInstitucional**: Franja o conjunto de franjas retiradas de la reserva libre. Atributos: espacio, fechas y horas bloqueadas, tipo (clases y grupos formativos, eventos o torneos, o mantenimiento), carácter puntual o recurrente, y motivo.
- **Reserva**: Entidad definida en la SPEC 003. Las afectadas pasan a "Cancelada por administrador".

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Prioridad Institucional, "0 reservas activas en franjas bloqueadas institucionalmente".
- **CE-002**: Equidad de Cuota, "100% de las reservas canceladas por un bloqueo no cuentan contra la cuota del Usuario".
- **CE-003**: Comunicación, "100% de los Usuarios con reservas afectadas reciben un aviso con el motivo".
