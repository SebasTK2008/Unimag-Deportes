# Especificación de Funcionalidad: UC12 - Contactar administrador

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Enviar un mensaje al área de deportes (Prioridad: P2)

Como Usuario, quiero enviar un mensaje mediante un formulario al correo del área de deportes, de manera que pueda comunicarme con el administrador.

**Por qué esta prioridad**: Es un canal de apoyo; no interviene en el flujo de reserva, pero los correos de la plataforma deben incluir un canal para contactar al administrador (RN-48).

**Prueba Independiente**: Enviar un mensaje desde el formulario y validar que llega al correo del área de deportes.

**Escenarios de Aceptación**:

1. **Escenario**: Envío del mensaje.
   - **Dado** que el Usuario está autenticado.
   - **Cuando** completa el formulario de contacto y lo envía.
   - **Entonces** el sistema envía el mensaje al correo del área de deportes.

### Casos Extremos (Edge Cases)

- **¿Cuál es el canal de contacto que deben incluir los correos de la plataforma?**
  **[Pendiente de definir]** RN-48 exige un canal para contactar al administrador, pero el contexto no precisa si es este formulario.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE ofrecer al Usuario un formulario de contacto (RF-10).
- **RF-002**: El sistema DEBE enviar el mensaje del formulario al correo del área de deportes (RF-10).

### Requisitos No Funcionales

- **RNF-001**: El formulario DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto) y cumplir WCAG (RNF-05 del contexto).

### Entidades Clave

- **MensajeDeContacto (DTO)**: Mensaje redactado por el Usuario para el área de deportes. No se persiste; se envía por correo.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Entrega, "100% de los mensajes enviados desde el formulario se dirigen al correo del área de deportes".
