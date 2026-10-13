# Especificación de Funcionalidad: UC18 - Enviar notificaciones por correo

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Notificar por correo los eventos de una reserva (Prioridad: P1)

Como el sistema, al ocurrir un evento relevante sobre la reserva de un Usuario, quiero enviar una notificación mediante el Servicio de correo a su correo institucional, de manera que el Usuario esté informado de recordatorios, cancelaciones, bloqueos, inasistencias y suspensiones.

**Por qué esta prioridad**: El Usuario depende de estos avisos para conocer cambios en sus reservas y en su situación (RN-47). El comprobante de reserva se especifica aparte (SPEC 004).

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

- **¿Qué incluyen todos los correos?**
  Un canal para contactar al administrador (RN-48).

- **¿Qué notificaciones cubre esta SPEC?**
  Recordatorio, cancelaciones, bloqueos que afecten una reserva, inasistencias y suspensiones. El comprobante de la reserva se especifica en la SPEC 004.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE enviar un recordatorio 2 horas antes del inicio de la reserva, con enlace para cancelar (RN-46).
- **RF-002**: El sistema DEBE notificar por correo cada cancelación (RN-26, RN-47).
- **RF-003**: El sistema DEBE notificar por correo los bloqueos que afecten una reserva, con el motivo (RN-43, RN-47).
- **RF-004**: El sistema DEBE notificar por correo las inasistencias y las suspensiones; el aviso de suspensión incluye la fecha en que el Usuario podrá reservar de nuevo (RN-39, RN-47).
- **RF-005**: El sistema DEBE incluir en los correos un canal para contactar al administrador (RN-48).
- **RF-006**: El sistema DEBE entregar las notificaciones mediante el Servicio de correo al correo institucional del Usuario (RF-20).

### Requisitos No Funcionales

- **RNF-001**: Las fechas y horas de los correos DEBEN expresarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-002**: Los correos DEBEN contener únicamente los datos necesarios del Usuario: nombre y correo institucional (RNF-09 del contexto).

### Entidades Clave

- **NotificacionCorreo (DTO)**: Mensaje enviado al correo institucional del Usuario. Contiene destinatario, tipo (recordatorio, cancelación, bloqueo, inasistencia o suspensión), contenido y canal de contacto con el administrador. No se define su persistencia en el contexto.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cobertura, "100% de los eventos de recordatorio, cancelación, bloqueo, inasistencia y suspensión generan su notificación por correo".
- **CE-002**: Contacto, "100% de los correos incluyen un canal para contactar al administrador".
