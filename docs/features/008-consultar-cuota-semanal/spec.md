# Especificación de Funcionalidad: UC08 - Consultar cuota semanal

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Ver mis horas usadas y disponibles de la semana (Prioridad: P1)

Como Usuario, quiero ver en todo momento mis horas usadas, mis horas disponibles y la fecha de reinicio de mi cuota semanal, de manera que sepa cuánto puedo reservar antes de intentarlo.

**Por qué esta prioridad**: La cuota es el único mecanismo de control de la plataforma, que no tiene cobros monetarios (principio 1 del contexto); conocerla evita rechazos al reservar (RN-18).

**Prueba Independiente**: Con un Usuario con reservas en distintos estados, consultar la cuota y validar que las horas usadas y disponibles reflejan lo que cuenta según el estado de cada reserva.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de la cuota.
   - **Dado** que el Usuario tiene 2 horas contadas en la semana y la cuota semanal es de 4 horas.
   - **Cuando** consulta su cuota semanal.
   - **Entonces** el sistema muestra 2 horas usadas, 2 horas disponibles y la fecha de reinicio.

2. **Escenario**: Cancelación a tiempo.
   - **Dado** que el Usuario cancela a tiempo una reserva de 1 hora.
   - **Cuando** consulta su cuota.
   - **Entonces** el sistema muestra esa hora devuelta como disponible.

3. **Escenario**: Cancelación tardía o inasistencia.
   - **Dado** que el Usuario tiene una reserva cancelada tardíamente o una inasistencia.
   - **Cuando** consulta su cuota.
   - **Entonces** el sistema sigue contando esas horas como usadas.

---

### Historia de Usuario 2 - Reiniciar la cuota de cada Usuario cada semana (Prioridad: P1)

Como el sistema, al comenzar cada semana de conteo, quiero reiniciar la cuota semanal de los Usuarios al límite configurado, de manera que cada Usuario cuente con sus horas completas en la nueva semana.

**Por qué esta prioridad**: La cuota semanal es el único mecanismo de control de uso; su reinicio periódico mantiene el funcionamiento de las reservas.

**Prueba Independiente**: Con Usuarios con horas contadas en una semana, validar que al iniciar la semana siguiente (lunes 00:00, zona horaria America/Bogota) la cuota vuelve al límite configurado, considerando las reservas que ya estén hechas para esa semana.

**Escenarios de Aceptación**:

1. **Escenario**: Inicio de una nueva semana.
   - **Dado** que comienza una nueva semana de conteo.
   - **Cuando** llega el lunes a las 00:00 en zona horaria America/Bogota.
   - **Entonces** la cuota semanal de los Usuarios vuelve al límite configurado (4 horas por defecto).

2. **Escenario**: Reservas ya hechas para la semana nueva.
   - **Dado** que un Usuario hizo una reserva para una fecha de la semana siguiente.
   - **Cuando** comienza esa semana.
   - **Entonces** la reserva cuenta en la cuota de esa semana, ya que la semana de una reserva es la de la fecha de la franja.

### Casos Extremos (Edge Cases)

- **¿Qué horas cuentan contra la cuota según el estado de la reserva?**
  Cuentan: confirmada, en uso, completada, cancelada tardía e inasistencia. No cuentan: cancelada a tiempo y cancelada por el administrador (sección 5.4 del contexto).

- **¿En qué semana se cuenta una reserva?**
  En la de la fecha de la franja, no en la de creación (RN-17). Una reserva creada con anticipación cuenta en la semana de su franja.

- **¿Cuándo comienza la semana de conteo y se reinicia la cuota?**
  El lunes a las 00:00 en zona horaria America/Bogota (sección 4 del contexto), valor configurable (ver SPEC 006).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE mostrar al Usuario sus horas usadas, sus horas disponibles y la fecha de reinicio de la cuota semanal (RF-07).
- **RF-002**: El sistema DEBE permitir esta consulta en todo momento (RN-19).
- **RF-003**: El sistema DEBE calcular las horas usadas según el estado de cada reserva, conforme a la regla de cuota de la SPEC 003 (RN-16).
- **RF-004**: El sistema DEBE usar el límite semanal configurado, 4 horas por defecto (sección 4 del contexto).
- **RF-005**: El sistema DEBE reiniciar la cuota semanal cada semana al límite configurado (RF-21).
- **RF-006**: El sistema DEBE iniciar la semana de conteo el lunes a las 00:00 en zona horaria America/Bogota (sección 4 del contexto).
- **RF-007**: El sistema DEBE contar cada reserva en la semana de la fecha de la franja (RN-17).

### Requisitos No Funcionales

- **RNF-001**: La consulta DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-002**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **CuotaSemanal (DTO)**: Resultado de la consulta. Contiene horas usadas, horas disponibles y fecha de reinicio. No se persiste como entidad independiente: se obtiene de las Reservas del Usuario (ver SPEC 003) y del límite configurado (SPEC 006). El reinicio semanal (Historia de Usuario 2) la devuelve al límite configurado, descontando las reservas ya hechas para esa semana.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Exactitud, "100% de las consultas de cuota coinciden con la suma de horas de las reservas que cuentan según su estado".
- **CE-002**: Coherencia con Reservas, "0 diferencias entre las horas disponibles mostradas y el rechazo o aceptación de una nueva reserva por cuota".
- **CE-003**: Reinicio Correcto, "100% de las cuotas semanales vuelven al límite configurado al iniciar la semana, descontando las reservas ya hechas para esa semana".
