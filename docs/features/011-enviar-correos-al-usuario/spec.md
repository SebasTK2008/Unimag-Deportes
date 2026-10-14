# Especificación de Funcionalidad: UC11 - Enviar correos al usuario

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Recibir el comprobante de la reserva en PDF (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Reservar espacio deportivo" una vez confirmada la reserva, quiero generar un comprobante en PDF y entregarlo mediante el Servicio de correo al correo institucional del Usuario, de manera que el Usuario tenga constancia de su reserva.

**Por qué esta prioridad**: El comprobante forma parte de la confirmación de toda reserva (RN-13); sin él, el Usuario no recibe su constancia de espacio, fecha, hora y reglas de cancelación.

**Prueba Independiente**: Confirmar una reserva y validar que se envía al correo institucional del Usuario un PDF con espacio, fecha, hora, nombre del Usuario y reglas de cancelación.

**Escenarios de Aceptación**:

1. **Escenario**: Envío del comprobante tras confirmar una reserva.
   - **Dado** que una reserva fue confirmada.
   - **Cuando** el sistema es invocado desde "Reservar espacio deportivo".
   - **Entonces** el sistema genera un PDF con espacio, fecha, hora, nombre del Usuario y reglas de cancelación, y el Servicio de correo lo entrega al correo institucional del Usuario.

2. **Escenario**: El correo incluye un canal de contacto.
   - **Dado** que se envía el comprobante.
   - **Cuando** el Servicio de correo lo entrega.
   - **Entonces** el correo incluye un canal para contactar al administrador.

---

### Historia de Usuario 2 - Notificar por correo los eventos de una reserva (Prioridad: P1)

Como el sistema, al ocurrir un evento relevante sobre la reserva de un Usuario, quiero enviar una notificación mediante el Servicio de correo a su correo institucional, de manera que el Usuario esté informado de recordatorios, cancelaciones, bloqueos, inasistencias y suspensiones.

**Por qué esta prioridad**: El Usuario depende de estos avisos para conocer cambios en sus reservas y en su situación (RN-47).

**Prueba Independiente**: Provocar cada evento (recordatorio, cancelación, bloqueo, inasistencia, suspensión) y validar que el Usuario recibe el correo correspondiente con su contenido y el canal de contacto.

**Escenarios de Aceptación**:

1. **Escenario**: Recordatorio previo a la reserva.
   - **Dado** que una reserva está confirmada.
   - **Cuando** faltan 2 horas para su inicio.
   - **Entonces** el Usuario recibe un recordatorio con un enlace para cancelar.

2. **Escenario**: Cancelación.
   - **Dado** que se cancela una reserva.
   - **Cuando** la cancelación queda registrada.
   - **Entonces** el Usuario recibe una notificación de la cancelación.

3. **Escenario**: Bloqueo que afecta una reserva.
   - **Dado** que un bloqueo institucional cancela una reserva.
   - **Cuando** el sistema procesa el bloqueo.
   - **Entonces** el Usuario recibe un aviso con el motivo.

4. **Escenario**: Inasistencia y suspensión.
   - **Dado** que se registra una inasistencia o se suspende al Usuario.
   - **Cuando** el sistema lo procesa.
   - **Entonces** el Usuario recibe un correo; el de suspensión indica la fecha en que podrá reservar de nuevo.

### Casos Extremos (Edge Cases)

- **¿A quién se envían los correos?**
  Al correo institucional del Usuario dueño de la reserva, ya que las reservas son nominales (RN-03, RN-45).

- **¿Se envía comprobante a toda reserva confirmada?**
  Sí. Al confirmar una reserva, el sistema envía el comprobante (RN-13).

- **¿Qué sucede si el Servicio de correo no puede entregar un correo?**
  **[Pendiente de definir]** El contexto no establece este comportamiento, ni para el comprobante ni para las notificaciones.

- **¿Qué incluyen todos los correos?**
  Un canal para contactar al administrador (RN-48).

- **¿Qué notificaciones cubre la Historia de Usuario 2?**
  Recordatorio, cancelaciones, bloqueos que afecten una reserva, inasistencias y suspensiones. El comprobante de la reserva se especifica en la Historia de Usuario 1.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE generar un comprobante en PDF con espacio, fecha, hora, nombre del Usuario y reglas de cancelación al confirmarse una reserva (RF-04, RN-45).
- **RF-002**: El sistema DEBE entregar el comprobante al correo institucional del Usuario mediante el Servicio de correo (RF-04, RN-45).
- **RF-003**: El sistema DEBE incluir en el correo un canal para contactar al administrador (RN-48).
- **RF-004**: El sistema DEBE ejecutar la generación y el envío del comprobante al ser invocado por "Reservar espacio deportivo" (`<<include>>`).
- **RF-005**: El sistema DEBE enviar un recordatorio 2 horas antes del inicio de la reserva, con enlace para cancelar (RN-46).
- **RF-006**: El sistema DEBE notificar por correo cada cancelación (RN-26, RN-47).
- **RF-007**: El sistema DEBE notificar por correo los bloqueos que afecten una reserva, con el motivo (RN-43, RN-47).
- **RF-008**: El sistema DEBE notificar por correo las inasistencias y las suspensiones; el aviso de suspensión incluye la fecha en que el Usuario podrá reservar de nuevo (RN-39, RN-47).
- **RF-009**: El sistema DEBE incluir en las notificaciones un canal para contactar al administrador (RN-48).
- **RF-010**: El sistema DEBE entregar las notificaciones mediante el Servicio de correo al correo institucional del Usuario (RF-20).

### Requisitos No Funcionales

- **RNF-001**: Las fechas y horas del comprobante y de los correos DEBEN expresarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-002**: El comprobante y los correos DEBEN contener únicamente los datos necesarios del Usuario: nombre y correo institucional (RNF-09 del contexto).

### Entidades Clave

- **ComprobanteReserva (DTO)**: Documento PDF generado a partir de la Reserva (ver SPEC 003). Contiene espacio, fecha, hora, nombre del Usuario, reglas de cancelación y el canal de contacto con el administrador. No se define su persistencia en el contexto.
- **NotificacionCorreo (DTO)**: Mensaje enviado al correo institucional del Usuario. Contiene destinatario, tipo (recordatorio, cancelación, bloqueo, inasistencia o suspensión), contenido y canal de contacto con el administrador. No se define su persistencia en el contexto.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cobertura de Confirmaciones, "100% de las reservas confirmadas generan un comprobante en PDF dirigido al correo institucional del Usuario".
- **CE-002**: Completitud del Contenido, "100% de los comprobantes incluyen espacio, fecha, hora, nombre del Usuario, reglas de cancelación y canal de contacto con el administrador".
- **CE-003**: Cobertura de Notificaciones, "100% de los eventos de recordatorio, cancelación, bloqueo, inasistencia y suspensión generan su notificación por correo".
- **CE-004**: Contacto, "100% de las notificaciones incluyen un canal para contactar al administrador".
