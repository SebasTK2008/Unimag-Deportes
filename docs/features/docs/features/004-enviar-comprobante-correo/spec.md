# Especificación de Funcionalidad: UC04 - Enviar comprobante al correo (PDF)

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

### Casos Extremos (Edge Cases)

- **¿A quién se envía el comprobante?**
  Al correo institucional del Usuario que creó la reserva, ya que las reservas son nominales (RN-03, RN-45).

- **¿Se envía comprobante a toda reserva confirmada?**
  Sí. Al confirmar una reserva, el sistema envía el comprobante (RN-13).

- **¿Qué sucede si el Servicio de correo no puede entregar el comprobante?**
  **[Pendiente de definir]** El contexto no establece este comportamiento.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE generar un comprobante en PDF con espacio, fecha, hora, nombre del Usuario y reglas de cancelación al confirmarse una reserva (RF-04, RN-45).
- **RF-002**: El sistema DEBE entregar el comprobante al correo institucional del Usuario mediante el Servicio de correo (RF-04, RN-45).
- **RF-003**: El sistema DEBE incluir en el correo un canal para contactar al administrador (RN-48).
- **RF-004**: El sistema DEBE ejecutar este caso de uso al ser invocado por "Reservar espacio deportivo" (`<<include>>`).

### Requisitos No Funcionales

- **RNF-001**: Las fechas y horas del comprobante DEBEN expresarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-002**: El comprobante DEBE contener únicamente los datos necesarios del Usuario: nombre y correo institucional (RNF-09 del contexto).

### Entidades Clave

- **ComprobanteReserva (DTO)**: Documento PDF generado a partir de la Reserva (ver SPEC 003). Contiene espacio, fecha, hora, nombre del Usuario, reglas de cancelación y el canal de contacto con el administrador. No se define su persistencia en el contexto.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cobertura de Confirmaciones, "100% de las reservas confirmadas generan un comprobante en PDF dirigido al correo institucional del Usuario".
- **CE-002**: Completitud del Contenido, "100% de los comprobantes incluyen espacio, fecha, hora, nombre del Usuario, reglas de cancelación y canal de contacto con el administrador".
