# Especificación de Funcionalidad: UC17 - Aplicar sanciones

**Creado**: 2026-10-13  

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
   - **Entonces** el Usuario recibe un correo informando la inasistencia (ver SPEC 018).

### Historia de Usuario 2 - Suspender al Usuario por inasistencias reiteradas (Prioridad: P2)

Como el sistema, al registrar la tercera inasistencia de un Usuario dentro de 30 días, quiero suspenderlo automáticamente por 7 días, de manera que no pueda crear nuevas reservas durante ese período.

**Por qué esta prioridad**: Es la sanción para quienes reinciden en las inasistencias; depende de que la Historia 1 funcione.

**Prueba Independiente**: Registrar tres inasistencias de un mismo Usuario en 30 días y validar que queda suspendido por 7 días, que no puede crear reservas, que conserva sus reservas futuras y que recibe un correo con la fecha en que podrá reservar de nuevo.

**Escenarios de Aceptación**:

1. **Escenario**: Suspensión por tres inasistencias.
   - **Dado** que un Usuario acumula tres inasistencias en 30 días.
   - **Cuando** se registra la tercera.
   - **Entonces** el sistema lo suspende automáticamente por 7 días.

2. **Escenario**: Efecto de la suspensión.
   - **Dado** que el Usuario está suspendido.
   - **Cuando** intenta crear una reserva.
   - **Entonces** el sistema la rechaza, pero sus reservas futuras se mantienen y puede consultar disponibilidad y sus reservas.

3. **Escenario**: Aviso de la suspensión.
   - **Dado** que el Usuario fue suspendido.
   - **Cuando** el sistema lo procesa.
   - **Entonces** el Usuario recibe un correo con la fecha en que podrá reservar de nuevo (ver SPEC 018).

### Casos Extremos (Edge Cases)

- **¿Hay multas?**
  No. La sanción es perder las horas y, de repetirse, una suspensión temporal (sección 5.8 del contexto).

- **¿Cuándo cierra la ventana de check-in en una reserva hecha dentro de la tolerancia?**
  Su plazo es de 10 minutos desde la creación de la reserva (RN-30); al vencer sin check-in, la reserva pasa a "Inasistencia".

- **¿Qué ocurre con el conteo de inasistencias después de aplicada una suspensión?**
  **[Pendiente de definir]** El contexto no lo establece.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE marcar como "Inasistencia" la reserva sin check-in al cerrar la ventana y mantener sus horas contra la cuota (RN-37, RF-19).
- **RF-002**: El sistema DEBE suspender automáticamente por 7 días al Usuario con 3 inasistencias en 30 días (RN-38).
- **RF-003**: El sistema DEBE notificar por correo al Usuario cuando se registre una inasistencia y cuando sea suspendido, con la fecha en que podrá reservar de nuevo (RN-39).
- **RF-004**: El sistema DEBE mantener las reservas futuras de un Usuario suspendido e impedirle crear nuevas (RN-40, RN-05).
- **RF-005**: El sistema DEBE tomar los umbrales de suspensión de los parámetros configurables (sección 4 del contexto; ver SPEC 009).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE registrar auditoría de las sanciones (RNF-08 del contexto).
- **RNF-002**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Reserva**: Entidad definida en la SPEC 003. Esta función la pasa a estado "Inasistencia".
- **Usuario**: Entidad definida en la SPEC 001. Esta función le asigna un estado de suspensión con la fecha en que podrá reservar de nuevo.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Aplicación de Sanciones, "100% de las reservas sin check-in al cerrar la ventana pasan a 'Inasistencia' y conservan sus horas contra la cuota".
- **CE-002**: Suspensión Correcta, "100% de los Usuarios con 3 inasistencias en 30 días quedan suspendidos por 7 días y reciben el aviso correspondiente".
