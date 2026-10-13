# Especificación de Funcionalidad: UC05 - Consultar mis reservas

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

### Casos Extremos (Edge Cases)

- **¿Qué estados puede mostrar una reserva?**
  Confirmada, En uso, Completada, Cancelada a tiempo, Cancelada tardía, Inasistencia y Cancelada por administrador (sección 6 del contexto).

- **¿Se mantienen las reservas futuras de un Usuario suspendido?**
  Sí. Se mantienen, aunque el Usuario no puede crear nuevas (RN-40, RN-05).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE mostrar al Usuario la lista de sus reservas próximas y pasadas con su estado (RF-05).
- **RF-002**: El sistema DEBE mostrar únicamente las reservas creadas por el Usuario autenticado, dado que son nominales (RN-03).
- **RF-003**: El sistema DEBE permitir esta consulta a un Usuario suspendido (RN-05).
- **RF-004**: El sistema DEBE ofrecer desde "Mis reservas" la acción de cancelar reserva (RN-20).

### Requisitos No Funcionales

- **RNF-001**: La consulta DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-002**: Las fechas y horas DEBEN mostrarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Reserva**: Entidad definida en la SPEC 003. Aquí solo se consulta; se muestran espacio, fecha, franja(s) y estado.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Exactitud, "100% de las reservas de un Usuario aparecen en 'Mis reservas' con su estado vigente".
- **CE-002**: Privacidad de Reservas, "0 reservas de otros Usuarios visibles en 'Mis reservas'".
