# Especificación de Funcionalidad: UC03 - Reservar espacio deportivo

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reservar una o dos franjas con confirmación automática (Prioridad: P1)

Como Usuario autenticado, quiero elegir disciplina, espacio, fecha y franja(s) y confirmar la reserva, de manera que el sistema la confirme al instante, sin aprobación manual, cuando se cumplan las reglas de uso, y envíe el comprobante a mi correo.

**Por qué esta prioridad**: Es la funcionalidad central de la plataforma y reemplaza el proceso actual con formularios. Concentra las validaciones (correo, suspensión, disponibilidad, cuota, solapes) y el bloqueo transaccional que garantiza "cero cruces". Invoca internamente "Consultar disponibilidad" y "Enviar comprobante al correo (PDF)" (`<<include>>`).

**Prueba Independiente**: Crear reservas con un Usuario válido y con casos que incumplan cada regla (franja ocupada, cuota agotada, usuario suspendido, solape), y validar que solo se confirman las que cumplen todas las validaciones y que ninguna franja queda con dos reservas activas.

**Escenarios de Aceptación**:

1. **Escenario**: Reserva confirmada de una franja.
   - **Dado** que el Usuario está autenticado, no está suspendido, la franja está libre dentro de los 7 días de anticipación y tiene cuota suficiente.
   - **Cuando** elige disciplina, espacio, fecha y franja, y confirma.
   - **Entonces** el sistema confirma la reserva al instante, bloquea la franja en la misma operación transaccional y envía el comprobante al correo.

2. **Escenario**: Reserva de dos franjas consecutivas.
   - **Dado** que existen dos franjas consecutivas libres del mismo espacio y el Usuario tiene cuota suficiente.
   - **Cuando** el Usuario confirma la reserva de ambas.
   - **Entonces** la reserva abarca las dos franjas (2 horas) y ambas cuentan contra la cuota semanal.

3. **Escenario**: Dos usuarios reservan la misma franja a la vez.
   - **Dado** que dos Usuarios intentan reservar la misma franja al mismo tiempo.
   - **Cuando** el sistema verifica y bloquea la franja.
   - **Entonces** gana el primero y el segundo recibe el aviso "La franja ya no está disponible" con el calendario actualizado.

4. **Escenario**: Cuota semanal insuficiente.
   - **Dado** que el Usuario ya tiene 3 horas contadas en la semana de la franja y la cuota semanal es de 4 horas.
   - **Cuando** intenta reservar 2 horas más.
   - **Entonces** el sistema rechaza la reserva e informa que le queda 1 hora.

5. **Escenario**: Usuario suspendido.
   - **Dado** que el Usuario está suspendido.
   - **Cuando** intenta crear una reserva.
   - **Entonces** el sistema rechaza la reserva.

6. **Escenario**: Solape con otra reserva del mismo Usuario.
   - **Dado** que el Usuario ya tiene una reserva que se solapa en el tiempo, aunque sea en otro espacio.
   - **Cuando** intenta reservar.
   - **Entonces** el sistema rechaza la reserva.

7. **Escenario**: Reserva dentro de la tolerancia.
   - **Dado** que una franja libre ya comenzó y no han pasado más de 10 minutos desde su inicio.
   - **Cuando** el Usuario la reserva.
   - **Entonces** la reserva queda confirmada para la franja completa, cuenta la franja completa contra la cuota y el Usuario debe hacer check-in dentro de los 10 minutos siguientes a la creación de la reserva.

**Regla de cuota semanal** (RN-16 a RN-18):

- La cuota se mide en horas reservadas, no solo en horas usadas. El valor por defecto es de 4 horas por semana y es configurable.
- La semana de una reserva es la de la fecha de la franja, no la de creación.
- Antes de confirmar, el sistema suma las horas que ya cuentan en esa semana más las de la nueva reserva; si superan la cuota, rechaza e informa cuántas horas le quedan al Usuario.
- Cuentan contra la cuota: confirmada, en uso, completada, cancelada tardía e inasistencia. No cuentan: cancelada a tiempo y cancelada por el administrador.

### Casos Extremos (Edge Cases)

- **¿Con cuánta anticipación y hasta cuándo se puede reservar?**
  Con hasta 7 días de anticipación (RN-14). La anticipación mínima es de 0 minutos: se permite reservar hasta el fin de la tolerancia (10 minutos después del inicio); pasada la tolerancia la franja deja de ser reservable aunque siga libre (RN-27, RN-28).

- **¿Cuántas franjas puede abarcar una reserva?**
  Hasta 2 franjas consecutivas del mismo espacio (RN-15).

- **¿Qué ocurre al reservar un espacio que sirve para varias disciplinas?**
  La reserva bloquea el espacio completo, sin importar la disciplina elegida (RN-07).

- **¿Se puede reservar una franja bloqueada institucionalmente o un espacio en mantenimiento?**
  No. Los bloqueos institucionales tienen prioridad sobre las reservas libres (RN-42) y un espacio en mantenimiento no aparece como reservable (RN-09).

- **¿La reserva se puede transferir o hacer a nombre de otra persona?**
  No. Las reservas son nominales: quedan a nombre del Usuario que las crea, quien es el que debe asistir y hacer el check-in (RN-03, RN-04).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario elegir disciplina, espacio, fecha y franja(s), y confirmar la reserva (RF-03).
- **RF-002**: El sistema DEBE invocar "Consultar disponibilidad" para presentar las franjas reservables (`<<include>>`).
- **RF-003**: El sistema DEBE validar en cada reserva que el correo sea válido, que el Usuario no esté suspendido, que la franja esté libre y que el Usuario tenga cuota suficiente (RN-12, RF-17).
- **RF-004**: El sistema DEBE ejecutar la verificación y el bloqueo de la franja en una sola operación transaccional; si dos Usuarios compiten, gana el primero y el segundo recibe el aviso "La franja ya no está disponible" con el calendario actualizado (RN-10, RF-18).
- **RF-005**: El sistema DEBE garantizar que un espacio tenga una sola reserva activa por franja y que una reserva bloquee el espacio completo sin importar la disciplina (RN-07, RN-10).
- **RF-006**: El sistema NO DEBE permitir que un Usuario tenga dos reservas que se solapen en el tiempo, aunque sean en espacios distintos (RN-11).
- **RF-007**: El sistema DEBE permitir que una reserva abarque hasta 2 franjas consecutivas del mismo espacio (RN-15).
- **RF-008**: El sistema DEBE permitir reservar con hasta 7 días de anticipación y hasta el fin de la tolerancia de la franja (RN-14, RN-27, RN-28).
- **RF-009**: El sistema DEBE aplicar la regla de cuota semanal descrita en esta SPEC, sumando las horas de la semana de la fecha de la franja, y rechazar la reserva informando las horas restantes cuando se supere la cuota (RN-16 a RN-18).
- **RF-010**: El sistema DEBE, para una reserva hecha dentro de la tolerancia, ocupar la franja completa, contar la franja completa contra la cuota y exigir el check-in dentro de los 10 minutos siguientes a su creación (RN-29, RN-30).
- **RF-011**: El sistema DEBE confirmar la reserva al instante en pantalla, sin aprobación manual, cuando todas las validaciones pasen (RN-13).
- **RF-012**: El sistema DEBE invocar "Enviar comprobante al correo (PDF)" al confirmar la reserva (RN-13, RN-45, `<<include>>`).

### Requisitos No Funcionales

- **RNF-001**: El Usuario DEBE poder reservar en 5 pasos o menos (RNF-01 del contexto).
- **RNF-002**: El flujo DEBE ser *mobile-first*: resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-003**: La meta de usabilidad es completar una reserva en 40 segundos o menos (RNF-03 del contexto, KPI para las pruebas de usabilidad).
- **RNF-004**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota, con el inicio de la semana de conteo el lunes a las 00:00 (RNF-07 y sección 4 del contexto).
- **RNF-005**: El sistema DEBE registrar auditoría de las reservas (RNF-08 del contexto).

### Entidades Clave

- **Reserva**: Asignación de una o dos franjas consecutivas de un espacio a un Usuario. Atributos: Usuario, espacio, fecha, franja(s), momento de creación y estado. Estados: Confirmada, En uso, Completada, Cancelada a tiempo, Cancelada tardía, Inasistencia y Cancelada por administrador. Toda reserva nace en estado Confirmada.
- Usuario, Espacio/Franja y parámetros de uso se definen en las SPEC 001, 011 y 009 respectivamente.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Integridad de Reservas, "0 franjas con más de una reserva activa, incluso con solicitudes simultáneas".
- **CE-002**: Cumplimiento de Reglas, "100% de las reservas confirmadas cumplen las validaciones de correo, suspensión, disponibilidad, solape y cuota semanal".
- **CE-003**: Usabilidad, "Una reserva se completa en 5 pasos o menos y en 40 segundos o menos en las pruebas de usabilidad".
