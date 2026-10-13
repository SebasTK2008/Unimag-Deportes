# Especificación de Funcionalidad: UC06 - Cancelar reserva

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Cancelar una reserva propia (Prioridad: P1)

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
   - **Entonces** el sistema envía una notificación por correo (ver SPEC 018).

**Regla de cancelación** (RN-21, RN-22):

- **A tiempo**: faltan 60 minutos o más. Estado "Cancelada a tiempo"; no cuenta contra la cuota.
- **Tardía**: faltan menos de 60 minutos, o la franja ya empezó sin check-in. Estado "Cancelada tardía"; sí cuenta contra la cuota.

### Casos Extremos (Edge Cases)

- **¿Puede el Administrador cancelar manualmente una reserva?**
  No. Solo el Usuario cancela su reserva desde "Mis reservas" (RN-20). La cancelación por parte del administrador ocurre únicamente de forma automática por un bloqueo institucional, con estado "Cancelada por administrador" y sin descontar horas (RN-43; ver SPEC 010).

- **¿Cuándo puede cancelar el Usuario?**
  En cualquier momento, sin autorización del administrador (RN-20), salvo que ya exista check-in (RN-24).

- **¿Qué ocurre justo a 60 minutos del inicio?**
  Se considera a tiempo: "faltan 60 minutos o más" (RN-21). Con menos de 60 minutos es tardía (umbral configurable, sección 4).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario cancelar su reserva desde "Mis reservas", sin autorización del administrador (RF-06, RN-20).
- **RF-002**: El sistema DEBE tratar como cancelación a tiempo la que se hace con 60 minutos o más de anticipación, liberar el espacio y devolver las horas a la cuota (RN-21).
- **RF-003**: El sistema DEBE tratar como cancelación tardía la que se hace con menos de 60 minutos de anticipación, o cuando la franja ya empezó sin check-in; el espacio queda disponible, pero las horas siguen contando (RN-22).
- **RF-004**: El sistema DEBE mostrar al Usuario, antes de confirmar, si la cancelación será tardía y qué efecto tendrá sobre su cuota (RN-25, RF-06).
- **RF-005**: El sistema NO DEBE permitir cancelar una reserva con check-in ya registrado (RN-24).
- **RF-006**: El sistema DEBE generar una notificación por correo en cada cancelación (RN-26).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE registrar auditoría de las cancelaciones (RNF-08 del contexto).
- **RNF-002**: El flujo DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-003**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Reserva**: Entidad definida en la SPEC 003. Esta función cambia su estado a "Cancelada a tiempo" o "Cancelada tardía" y determina si sus horas siguen contando contra la cuota.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cumplimiento de Reglas, "100% de las cancelaciones con 60 minutos o más devuelven las horas a la cuota y 100% de las tardías las conservan como usadas".
- **CE-002**: Integridad, "0 reservas con check-in registrado canceladas por el Usuario".
- **CE-003**: Transparencia, "100% de las cancelaciones muestran al Usuario su efecto sobre la cuota antes de confirmarse".
