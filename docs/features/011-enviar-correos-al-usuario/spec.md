# Especificación de Funcionalidad: UC11 - Enviar correos al usuario

**Creado**: 2026-10-13  
**Última revisión**: 2026-10-14 (se agrega el envío del código de acceso para el inicio de sesión)

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

---

### Historia de Usuario 3 - Recibir el código de acceso para iniciar sesión (Prioridad: P1)

Como el sistema, al ser invocado por "Iniciar sesión" (ver SPEC 001) cuando una persona solicita un código de acceso con un correo institucional, quiero enviar mediante el Servicio de correo un mensaje con el código de un solo uso a ese correo, de manera que la persona pueda completar su inicio de sesión sin contraseña.

**Por qué esta prioridad**: El código enviado al correo es la única forma de autenticarse en la plataforma (RN-49); si el correo no se envía o no llega, nadie puede ingresar, así que esta historia es prerrequisito de todos los demás casos de uso.

**Prueba Independiente**: Solicitar un código con un correo del dominio institucional y con uno de otro dominio, y validar que solo el primero recibe un correo, que llega en menos de 1 minuto, y que contiene el código, su vigencia, la advertencia de no compartirlo y el canal de contacto.

**Escenarios de Aceptación**:

1. **Escenario**: Envío del código de acceso.
   - **Dado** que una persona solicitó un código con un correo del dominio institucional y la solicitud cumplió las validaciones y límites de la SPEC 001.
   - **Cuando** el sistema genera el código.
   - **Entonces** el Servicio de correo entrega a ese correo un mensaje con el código de 6 dígitos, su vigencia de 10 minutos y la indicación de que no debe compartirse con nadie.

2. **Escenario**: Correo de otro dominio.
   - **Dado** que la solicitud se hizo con un correo que no pertenece al dominio institucional.
   - **Cuando** el sistema valida el dominio.
   - **Entonces** no se envía ningún correo (RN-50).

3. **Escenario**: Entrega oportuna.
   - **Dado** que se envió un código de acceso.
   - **Cuando** el Servicio de correo lo entrega en condiciones normales.
   - **Entonces** el correo llega en menos de 1 minuto.

4. **Escenario**: Envío de un código nuevo.
   - **Dado** que la persona solicita otro código dentro de los límites de solicitud de la SPEC 001.
   - **Cuando** el sistema genera el nuevo código.
   - **Entonces** el Servicio de correo entrega el nuevo código y el anterior queda inválido.

5. **Escenario**: El correo incluye un canal de contacto.
   - **Dado** que se envía el código de acceso.
   - **Cuando** el Servicio de correo lo entrega.
   - **Entonces** el correo incluye un canal para contactar al administrador.

### Casos Extremos (Edge Cases)

- **¿A quién se envían los correos?**
  Los comprobantes y las notificaciones, al correo institucional del Usuario dueño de la reserva, ya que las reservas son nominales (RN-03, RN-45). El código de acceso, al correo institucional que lo solicitó; como la persona puede ser nueva y aún no tener cuenta, el destinatario se determina por el correo ingresado, siempre que sea del dominio institucional (RN-50, RN-59).

- **¿Se envía comprobante a toda reserva confirmada?**
  Sí. Al confirmar una reserva, el sistema envía el comprobante (RN-13).

- **¿Qué sucede si el Servicio de correo no puede entregar el comprobante o una notificación?**
  **[Pendiente de definir]** El contexto no establece este comportamiento para el comprobante ni para las notificaciones.

- **¿Qué sucede si el Servicio de correo no puede entregar el código de acceso?**
  **[Pendiente de confirmar]** El contexto no lo establece. Propuesta: informar a la persona que no se pudo enviar el código y permitirle solicitar otro, respetando los límites de solicitud de la SPEC 001 (RN-53).

- **¿Qué incluyen todos los correos?**
  Un canal para contactar al administrador (RN-48). Aplica también al correo del código de acceso.

- **¿Qué notificaciones cubre la Historia de Usuario 2?**
  Recordatorio, cancelaciones, bloqueos que afecten una reserva, inasistencias y suspensiones. El comprobante de la reserva se especifica en la Historia de Usuario 1 y el código de acceso en la Historia de Usuario 3.

- **¿Se conserva el código de acceso después de enviarlo?**
  No en claro. El código solo viaja en el correo; el sistema guarda únicamente su hash (RN-51, RNF-09 del contexto) y el código no aparece en los registros de auditoría.

- **¿Los valores del código son definitivos?**
  La longitud (6 dígitos), la vigencia (10 minutos) y los límites de reenvío están marcados como propuestos en el contexto y por confirmar (RN-51, RN-53); el correo debe mostrar siempre los valores vigentes configurados.

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
- **RF-011**: El sistema DEBE enviar el código de acceso de un solo uso al correo institucional que lo solicitó, mediante el Servicio de correo, al ser invocado por "Iniciar sesión" (RF-20, RN-49; ver SPEC 001).
- **RF-012**: El sistema NO DEBE enviar ningún correo cuando el correo solicitante no pertenezca al dominio institucional (RN-50).
- **RF-013**: El correo con el código DEBE indicar el código de 6 dígitos, su vigencia y que no debe compartirse con nadie (RN-51, RNF-11 del contexto).
- **RF-014**: El sistema DEBE enviar un código nuevo cada vez que la persona lo solicite dentro de los límites de la SPEC 001, dejando inválido el anterior (RN-51, RN-53).
- **RF-015**: El sistema DEBE incluir en el correo del código de acceso un canal para contactar al administrador (RN-48).

### Requisitos No Funcionales

- **RNF-001**: Las fechas y horas del comprobante y de los correos DEBEN expresarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-002**: El comprobante y los correos DEBEN contener únicamente los datos necesarios del Usuario: nombre y correo institucional (RNF-09 del contexto).
- **RNF-003**: El correo con el código de acceso DEBE llegar en menos de 1 minuto en condiciones normales (RNF-11 del contexto).
- **RNF-004**: El sistema NO DEBE guardar el código de acceso en claro ni incluirlo en los registros de auditoría; solo se conserva su hash (RN-51, RNF-09 del contexto).

### Entidades Clave

- **ComprobanteReserva (DTO)**: Documento PDF generado a partir de la Reserva (ver SPEC 003). Contiene espacio, fecha, hora, nombre del Usuario, reglas de cancelación y el canal de contacto con el administrador. No se define su persistencia en el contexto.
- **NotificacionCorreo (DTO)**: Mensaje enviado al correo institucional del Usuario. Contiene destinatario, tipo (recordatorio, cancelación, bloqueo, inasistencia o suspensión), contenido y canal de contacto con el administrador. No se define su persistencia en el contexto.
- **CorreoCodigoAcceso (DTO)**: Mensaje enviado al correo institucional que solicitó iniciar sesión (ver SPEC 001). Contiene destinatario, el código de 6 dígitos, su vigencia, la indicación de no compartirlo y el canal de contacto con el administrador. No se persiste: el código solo existe en claro en el correo y el sistema conserva únicamente su hash.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cobertura de Confirmaciones, "100% de las reservas confirmadas generan un comprobante en PDF dirigido al correo institucional del Usuario".
- **CE-002**: Completitud del Contenido, "100% de los comprobantes incluyen espacio, fecha, hora, nombre del Usuario, reglas de cancelación y canal de contacto con el administrador".
- **CE-003**: Cobertura de Notificaciones, "100% de los eventos de recordatorio, cancelación, bloqueo, inasistencia y suspensión generan su notificación por correo".
- **CE-004**: Contacto, "100% de los correos de la plataforma (comprobante, notificaciones y código de acceso) incluyen un canal para contactar al administrador".
- **CE-005**: Entrega de Códigos, "100% de las solicitudes de código con un correo del dominio institucional que cumplen los límites generan un correo con el código, su vigencia y la advertencia de no compartirlo".
- **CE-006**: Restricción de Dominio, "0 correos con código de acceso enviados a direcciones fuera del dominio institucional".
- **CE-007**: Oportunidad, "100% de los correos con código de acceso llegan en menos de 1 minuto en condiciones normales".
- **CE-008**: Protección del Código, "0 códigos de acceso almacenados en claro o presentes en los registros de auditoría".