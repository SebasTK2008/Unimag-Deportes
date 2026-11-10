# Especificación de Funcionalidad: UC04 - Gestionar mis reservas

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Ver mis reservas próximas y pasadas con su estado (Prioridad: P1)

Como Usuario, quiero consultar la lista de mis reservas próximas y pasadas con su estado, de manera que pueda saber qué tengo reservado y desde allí cancelar una reserva cuando lo necesite.

**Por qué esta prioridad**: Es el punto de partida para cancelar reservas (RN-20) y para que el Usuario conozca el estado de cada una.

**Prueba Independiente**: Crear reservas en distintos estados para un Usuario y validar que la lista muestra solo sus reservas, próximas y pasadas, cada una con su estado.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de reservas próximas y pasadas.
   - **Dado** que el Usuario tiene reservas próximas y pasadas.
   - **Cuando** consulta "Mis reservas".
   - **Entonces** el sistema muestra la lista de sus reservas, próximas y pasadas, cada una con su estado.

2. **Escenario**: Solo se muestran las reservas propias.
   - **Dado** que existen reservas de otros Usuarios.
   - **Cuando** el Usuario consulta "Mis reservas".
   - **Entonces** el sistema muestra únicamente las reservas a su nombre.

3. **Escenario**: Consulta por un Usuario suspendido.
   - **Dado** que el Usuario está suspendido.
   - **Cuando** consulta "Mis reservas".
   - **Entonces** el sistema muestra sus reservas, incluidas las futuras, que se mantienen.

---

### Historia de Usuario 2 - Cancelar una reserva propia (Prioridad: P1)

Como Usuario, quiero cancelar mi reserva desde "Mis reservas" en cualquier momento, sin autorización del administrador, de manera que el espacio quede libre para otros y yo conozca antes de confirmar el efecto sobre mi cuota.

**Por qué esta prioridad**: Libera espacios para otros Usuarios y distingue la cancelación a tiempo de la tardía, que evita acaparar franjas y usar la cancha sin descontar horas (RN-23).

**Prueba Independiente**: Cancelar reservas con 60 minutos o más de anticipación, con menos de 60 minutos y con check-in registrado, y validar el efecto sobre el espacio y la cuota en cada caso.

**Escenarios de Aceptación**:

1. **Escenario**: Cancelación a tiempo.
   - **Dado** que faltan 60 minutos o más para el inicio de la franja.
   - **Cuando** el Usuario confirma la cancelación.
   - **Entonces** el espacio queda libre y las horas se devuelven a la cuota.

2. **Escenario**: Cancelación tardía.
   - **Dado** que faltan menos de 60 minutos para el inicio, o la franja ya empezó sin check-in.
   - **Cuando** el Usuario confirma la cancelación.
   - **Entonces** el espacio queda disponible para otros Usuarios, pero las horas siguen contando como usadas.

3. **Escenario**: Advertencia previa a la confirmación.
   - **Dado** que el Usuario inicia la cancelación de una reserva.
   - **Cuando** el sistema presenta la confirmación.
   - **Entonces** muestra si la cancelación será tardía y qué efecto tendrá sobre su cuota.

4. **Escenario**: Reserva con check-in registrado.
   - **Dado** que la reserva ya tiene check-in.
   - **Cuando** el Usuario intenta cancelarla.
   - **Entonces** el sistema no permite la cancelación.

5. **Escenario**: Notificación de la cancelación.
   - **Dado** que se cancela una reserva.
   - **Cuando** la cancelación queda registrada.
   - **Entonces** el sistema envía una notificación por correo (ver SPEC 011).

**Regla de cancelación** (RN-21, RN-22):

- **A tiempo**: faltan 60 minutos o más. Estado "Cancelada a tiempo"; no cuenta contra la cuota.
- **Tardía**: faltan menos de 60 minutos, o la franja ya empezó sin check-in. Estado "Cancelada tardía"; sí cuenta contra la cuota.

### Casos Extremos (Edge Cases)

- **¿Qué estados puede mostrar una reserva?**
  Confirmada, En uso, Completada, Cancelada a tiempo, Cancelada tardía, Inasistencia y Cancelada por administrador (sección 6 del contexto).

- **¿Se mantienen las reservas futuras de un Usuario suspendido?**
  Sí. Se mantienen, aunque el Usuario no puede crear nuevas (RN-40, RN-05).

- **¿Puede el Administrador cancelar manualmente una reserva?**
  No. Solo el Usuario cancela su reserva desde "Mis reservas" (RN-20). La cancelación por parte del administrador ocurre únicamente de forma automática por un bloqueo institucional, con estado "Cancelada por administrador" y sin descontar horas (RN-43; ver SPEC 007).

- **¿Cuándo puede cancelar el Usuario?**
  En cualquier momento, sin autorización del administrador (RN-20), salvo que ya exista check-in (RN-24).

- **¿Qué ocurre justo a 60 minutos del inicio?**
  Se considera a tiempo: "faltan 60 minutos o más" (RN-21). Con menos de 60 minutos es tardía (umbral configurable, sección 4).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE mostrar al Usuario la lista de sus reservas próximas y pasadas con su estado (RF-05).
- **RF-002**: El sistema DEBE mostrar únicamente las reservas creadas por el Usuario autenticado, dado que son nominales (RN-03).
- **RF-003**: El sistema DEBE permitir esta consulta a un Usuario suspendido (RN-05).
- **RF-004**: El sistema DEBE ofrecer desde "Mis reservas" la acción de cancelar reserva (RN-20).
- **RF-005**: El sistema DEBE permitir al Usuario cancelar su reserva desde "Mis reservas", sin autorización del administrador (RF-06, RN-20).
- **RF-006**: El sistema DEBE tratar como cancelación a tiempo la que se hace con 60 minutos o más de anticipación, liberar el espacio y devolver las horas a la cuota (RN-21).
- **RF-007**: El sistema DEBE tratar como cancelación tardía la que se hace con menos de 60 minutos de anticipación, o cuando la franja ya empezó sin check-in; el espacio queda disponible, pero las horas siguen contando (RN-22).
- **RF-008**: El sistema DEBE mostrar al Usuario, antes de confirmar, si la cancelación será tardía y qué efecto tendrá sobre su cuota (RN-25, RF-06).
- **RF-009**: El sistema NO DEBE permitir cancelar una reserva con check-in ya registrado (RN-24).
- **RF-010**: El sistema DEBE generar una notificación por correo en cada cancelación (RN-26).

### Requisitos No Funcionales

- **RNF-001**: Las funciones DEBEN poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-002**: Las fechas y horas DEBEN mostrarse y manejarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-003**: El sistema DEBE registrar auditoría de las cancelaciones (RNF-08 del contexto).

### Entidades Clave

- **Reserva**: Entidad definida en la SPEC 003. La consulta muestra espacio, fecha, franja(s) y estado; la cancelación cambia su estado a "Cancelada a tiempo" o "Cancelada tardía" y determina si sus horas siguen contando contra la cuota.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Exactitud, "100% de las reservas de un Usuario aparecen en 'Mis reservas' con su estado vigente".
- **CE-002**: Privacidad de Reservas, "0 reservas de otros Usuarios visibles en 'Mis reservas'".
- **CE-003**: Cumplimiento de Reglas, "100% de las cancelaciones con 60 minutos o más devuelven las horas a la cuota y 100% de las tardías las conservan como usadas".
- **CE-004**: Integridad, "0 reservas con check-in registrado canceladas por el Usuario".
- **CE-005**: Transparencia, "100% de las cancelaciones muestran al Usuario su efecto sobre la cuota antes de confirmarse".
