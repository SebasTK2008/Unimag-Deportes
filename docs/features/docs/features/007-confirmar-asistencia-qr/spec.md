# Especificación de Funcionalidad: UC07 - Confirmar asistencia (QR)

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registrar el check-in escaneando el QR del espacio (Prioridad: P1)

Como Usuario con una reserva, quiero escanear con mi celular el código QR del espacio al ingresar, de manera que el sistema confirme mi asistencia sin intervención de vigilantes ni del administrador y la reserva pase a "En uso".

**Por qué esta prioridad**: El check-in evita que la reserva se marque como inasistencia (RN-37) y es el único mecanismo de confirmación de asistencia definido.

**Prueba Independiente**: Escanear el QR de un espacio con distintas combinaciones de sesión, reserva y momento (dentro y fuera de la ventana), y validar que solo el caso válido pasa la reserva a "En uso" y que los demás muestran su motivo.

**Escenarios de Aceptación**:

1. **Escenario**: Check-in exitoso.
   - **Dado** que el Usuario está autenticado, tiene una reserva activa en ese espacio y el momento está dentro de la ventana de check-in.
   - **Cuando** escanea el QR del espacio.
   - **Entonces** el sistema registra el check-in y la reserva pasa a "En uso".

2. **Escenario**: Escaneo sin sesión iniciada.
   - **Dado** que el Usuario no tiene sesión iniciada.
   - **Cuando** escanea el QR.
   - **Entonces** el sistema le pide iniciar sesión y continúa con el check-in.

3. **Escenario**: Check-in fallido con motivo.
   - **Dado** que el Usuario no tiene reserva en ese espacio, es demasiado temprano, la ventana ya cerró o la reserva fue cancelada.
   - **Cuando** escanea el QR.
   - **Entonces** el sistema rechaza el check-in y muestra el motivo correspondiente.

4. **Escenario**: Check-in de una reserva hecha dentro de la tolerancia.
   - **Dado** que la reserva fue creada dentro de la tolerancia de la franja.
   - **Cuando** el Usuario escanea el QR dentro de los 10 minutos siguientes a la creación de la reserva.
   - **Entonces** el sistema registra el check-in y la reserva pasa a "En uso".

**Regla de la ventana de check-in** (RN-36, RN-30):

- Reserva general: desde 10 minutos antes del inicio hasta el fin de la tolerancia (10 minutos después del inicio).
- Reserva creada dentro de la tolerancia: dentro de los 10 minutos siguientes a su creación.

### Casos Extremos (Edge Cases)

- **¿Qué contiene el QR y cómo se identifica al Usuario?**
  El QR es estático y contiene un enlace fijo al espacio (por ejemplo, `/checkin/{id_espacio}`). No identifica al Usuario; la identidad sale de su sesión iniciada (RN-31, RN-32).

- **¿Quién debe hacer el check-in?**
  Quien creó la reserva, ya que es nominal (RN-04).

- **¿Qué sucede si no se hace el check-in al cerrar la ventana?**
  La reserva pasa a "Inasistencia" y sus horas cuentan contra la cuota (RN-37; ver SPEC 017).

- **¿Qué ocurre con una reserva con check-in registrado?**
  No se puede cancelar (RN-24). Al terminar la franja con asistencia, pasa de "En uso" a "Completada" (sección 6 del contexto).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario confirmar su asistencia escaneando el QR del espacio al ingresar, sin intervención de vigilantes ni del administrador (RF-08, RN-31).
- **RF-002**: El sistema DEBE comprobar, al escanear, que el Usuario esté autenticado, que tenga una reserva activa en ese espacio y que el momento esté dentro de la ventana de check-in; si todo coincide, la reserva pasa a "En uso" (RN-33).
- **RF-003**: El sistema DEBE pedir el inicio de sesión cuando no haya una sesión activa y continuar con el check-in una vez autenticado el Usuario (RN-34).
- **RF-004**: El sistema DEBE mostrar el motivo cuando el check-in falle: no tiene reserva en este espacio, es demasiado temprano, la ventana ya cerró o la reserva fue cancelada (RN-35).
- **RF-005**: El sistema DEBE aplicar la ventana de check-in desde 10 minutos antes del inicio hasta el fin de la tolerancia (RN-36) y, para reservas creadas dentro de la tolerancia, el plazo de 10 minutos desde su creación (RN-30).

### Requisitos No Funcionales

- **RNF-001**: El flujo de escaneo DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-002**: El sistema DEBE registrar auditoría de los check-ins (RNF-08 del contexto).
- **RNF-003**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Reserva**: Entidad definida en la SPEC 003. Esta función la pasa a estado "En uso".
- **Espacio**: Entidad definida en la SPEC 011. Cada espacio tiene un QR estático cuyo enlace fijo lo identifica (ver SPEC 016).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cumplimiento de Reglas, "100% de los check-in fuera de la ventana, sin reserva en el espacio o con la reserva cancelada son rechazados con su motivo".
- **CE-002**: Autonomía, "0 check-in que requieran intervención de un vigilante o del administrador".
