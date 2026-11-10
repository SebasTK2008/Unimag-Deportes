# Especificación de Funcionalidad: UC02 - Consultar disponibilidad

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el calendario de disponibilidad en tiempo real (Prioridad: P1)

Como Usuario o Administrador, o como el sistema al ser invocado internamente por "Reservar espacio deportivo", quiero consultar un calendario interactivo por espacio y disciplina que muestre las franjas libres, ocupadas y bloqueadas, de manera que se pueda identificar qué franjas están disponibles.

**Por qué esta prioridad**: Es el insumo de "Reservar espacio deportivo" (`<<include>>`) y la forma de saber qué franjas se pueden reservar antes de intentar hacerlo.

**Prueba Independiente**: Consultar el calendario de un espacio con franjas libres, ocupadas y bloqueadas, y validar que cada franja se muestra con su estado correcto y que un cambio de estado se refleja al instante.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta con franjas libres, ocupadas y bloqueadas.
   - **Dado** que existe un espacio activo con franjas de 60 minutos dentro de su horario de operación.
   - **Cuando** se consulta el calendario por espacio y disciplina.
   - **Entonces** el sistema muestra cada franja como libre, ocupada o bloqueada.

2. **Escenario**: Franja bloqueada institucionalmente.
   - **Dado** que una franja tiene un bloqueo institucional.
   - **Cuando** se consulta el calendario.
   - **Entonces** la franja aparece como no disponible con el motivo del bloqueo visible.

3. **Escenario**: Espacio compartido entre disciplinas.
   - **Dado** que un mismo espacio sirve para varias disciplinas y tiene una reserva en una franja.
   - **Cuando** se consulta el calendario por cualquiera de sus disciplinas.
   - **Entonces** la franja aparece ocupada, sin importar la disciplina con la que se hizo la reserva.

4. **Escenario**: Franja libre que ya comenzó.
   - **Dado** que una franja libre ya comenzó.
   - **Cuando** se consulta el calendario dentro de los 10 minutos siguientes a su inicio.
   - **Entonces** la franja sigue disponible para reservar; pasada la tolerancia deja de ser reservable aunque siga libre.

### Casos Extremos (Edge Cases)

- **¿Cómo aparece un espacio en mantenimiento?**
  No aparece como reservable (RN-09).

- **¿Cuándo se actualiza el calendario ante cambios de otros usuarios?**
  El calendario refleja los cambios al instante (RNF-04 del contexto). Cuando una reserva simultánea gana la franja, la persona recibe el aviso "La franja ya no está disponible" con el calendario actualizado (RN-10).

- **¿Puede consultar un Usuario suspendido?**
  Sí. Un Usuario suspendido puede consultar disponibilidad (RN-05).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE mostrar un calendario interactivo por espacio y disciplina con las franjas libres, ocupadas y bloqueadas (RF-02).
- **RF-002**: El sistema DEBE reflejar en el calendario los cambios de disponibilidad al instante (RNF-04 del contexto).
- **RF-003**: El sistema DEBE mostrar como ocupada la franja de un espacio compartido entre disciplinas cuando existe una reserva en él, sin importar la disciplina elegida (RN-07).
- **RF-004**: El sistema NO DEBE mostrar como reservable un espacio en mantenimiento (RN-09).
- **RF-005**: El sistema DEBE mostrar las franjas bloqueadas como no disponibles, con el motivo visible (RN-44).
- **RF-006**: El sistema DEBE mantener como disponible una franja libre que ya comenzó hasta el fin de la tolerancia y dejar de mostrarla como reservable pasada la tolerancia (RN-27, RN-28).
- **RF-007**: El sistema DEBE permitir esta consulta tanto de forma independiente (Usuario y Administrador) como al ser invocado por "Reservar espacio deportivo".

### Requisitos No Funcionales

- **RNF-001**: El calendario DEBE reflejar los cambios en tiempo real (RNF-04 del contexto).
- **RNF-002**: La consulta DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto) y cumplir WCAG (RNF-05 del contexto).
- **RNF-003**: Todas las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **FranjaDisponibilidad (DTO)**: Resultado de la consulta. Contiene el espacio, la fecha, la hora de inicio y fin de la franja, su estado (libre, ocupada o bloqueada) y el motivo del bloqueo cuando aplica. No se persiste; se construye a partir del Espacio (ver SPEC 007), las Reservas (SPEC 003) y los Bloqueos institucionales (SPEC 007).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Exactitud de Disponibilidad, "100% de las franjas con reserva activa o bloqueo institucional se muestran como no libres en el calendario".
- **CE-002**: Transparencia, "100% de las franjas bloqueadas se muestran con su motivo visible".
- **CE-003**: Consistencia, "0 espacios en mantenimiento mostrados como reservables".
