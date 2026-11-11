# Especificación de Funcionalidad: UC10 - Aplicar sanciones

**Creado**: 2026-10-13  
**Última revisión**: mes calendario y cancelación de reservas durante la suspensión.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registrar inasistencias y descontar horas (Prioridad: P1)

Como el sistema, al cerrar la ventana de check-in de una reserva sin asistencia registrada, quiero marcarla como "Inasistencia" y mantener sus horas contra la cuota, de manera que se aplique la sanción de perder las horas.

**Por qué esta prioridad**: Es la base de la sanción y de la suspensión; sin ella el check-in no tendría consecuencias (RN-37).

**Prueba Independiente**: Crear una reserva sin check-in, dejar cerrar la ventana y validar que pasa a "Inasistencia", que sus horas siguen contando y que el Usuario recibe un correo.

**Escenarios de Aceptación**:

1. **Escenario**: Reserva sin check-in al cerrar la ventana.
   - **Dado** que una reserva no tiene check-in.
   - **Cuando** se cierra su ventana de check-in.
   - **Entonces** la reserva pasa a "Inasistencia" y sus horas cuentan contra la cuota.

2. **Escenario**: Aviso de la inasistencia.
   - **Dado** que se registró una inasistencia.
   - **Cuando** el sistema la procesa.
   - **Entonces** el Usuario recibe un correo informando la inasistencia (ver SPEC 011).

### Historia de Usuario 2 - Suspender al Usuario por inasistencias reiteradas (Prioridad: P2)

Como el sistema, al registrar una inasistencia que alcance el umbral vigente en el mes calendario, quiero suspender al usuario durante el plazo configurado.

**Por qué esta prioridad**: Es la sanción para quienes reinciden en las inasistencias; depende de que la Historia 1 funcione.

**Prueba Independiente**: Registrar inasistencias hasta alcanzar el umbral vigente y verificar suspensión, cancelación de reservas futuras durante ese período, liberación de cuota e implementos y aviso.

**Escenarios de Aceptación**:

1. **Escenario**: Suspensión por tres inasistencias.
   - **Dado** que el umbral vigente es tres, la duración configurada es de 7 días y un Usuario acumula tres inasistencias en el mes calendario.
   - **Cuando** se registra la tercera.
   - **Entonces** el sistema lo suspende automáticamente por 7 días.

2. **Escenario**: Efecto de la suspensión.
   - **Dado** que el Usuario está suspendido.
   - **Cuando** intenta crear una reserva.
   - **Entonces** el sistema la rechaza y puede consultar disponibilidad y sus reservas; las reservas futuras durante la suspensión se cancelan, liberan cuota e implementos y generan notificación.

3. **Escenario**: Aviso de la suspensión.
   - **Dado** que el Usuario fue suspendido.
   - **Cuando** el sistema lo procesa.
   - **Entonces** el Usuario recibe un correo con la fecha en que podrá reservar de nuevo (ver SPEC 011).

### Casos Extremos (Edge Cases)

- **¿Hay multas?**
  No. La sanción es perder las horas y, de repetirse, una suspensión temporal (sección 5.8 del contexto).

- **¿Cuándo cierra la ventana de check-in en una reserva hecha dentro de la tolerancia?**
  La ventana termina al finalizar la reserva, sin incluir ese instante (RN-30); si no hay check-in, la reserva pasa a "Inasistencia".

- **¿Qué ocurre con el conteo de inasistencias después de aplicada una suspensión?**
  Se cuentan por mes calendario en America/Bogota y el día 1 comienza un nuevo conteo. El umbral se toma al registrar cada inasistencia, no de la reserva.

  Se aplica como máximo una suspensión por usuario y mes de inasistencia; las faltas posteriores del mismo mes se registran y notifican, sin otra suspensión por ese mes. Solo se cancelan reservas futuras contenidas por completo en el período de suspensión; las parcialmente solapadas se conservan.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE marcar como "Inasistencia" la reserva sin check-in al cerrar la ventana y mantener sus horas contra la cuota (RN-37, RF-19).
- **RF-002**: El sistema DEBE suspender automáticamente al Usuario que alcance el umbral vigente de inasistencias del mes calendario, durante la duración configurada (valores iniciales: 3 y 7 días; RN-38).
- **RF-003**: El sistema DEBE notificar por correo al Usuario cuando se registre una inasistencia y cuando sea suspendido, con la fecha en que podrá reservar de nuevo (RN-39).
- **RF-004**: El sistema DEBE cancelar las reservas futuras durante la suspensión, liberar sus horas e implementos, notificar e impedir nuevas reservas durante ese período (RN-40, RN-05).
- **RF-005**: El sistema DEBE tomar los umbrales de suspensión de los parámetros configurables (sección 4 del contexto; ver SPEC 006).
- **RF-006**: El sistema DEBE contar las inasistencias por mes calendario en America/Bogota y evaluar el umbral vigente al registrar cada una.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE registrar auditoría de las sanciones (RNF-08 del contexto).
- **RNF-002**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Reserva**: Entidad definida en la SPEC 003. Esta función la pasa a estado "Inasistencia".
- **Usuario**: Entidad definida en la SPEC 001. Esta función le asigna un estado de suspensión con la fecha en que podrá reservar de nuevo.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Aplicación de Sanciones, "100% de las reservas sin check-in al cerrar la ventana pasan a 'Inasistencia' y conservan sus horas contra la cuota".
- **CE-002**: Suspensión Correcta, "100% de los Usuarios que alcanzan el umbral vigente en el mes quedan suspendidos y se cancelan sus reservas futuras durante la suspensión, liberando recursos y notificando".
- **CE-003**: Conteo mensual, "100% de las inasistencias se cuentan en su mes calendario y el conteo comienza de nuevo el día 1".
