# Especificación de Funcionalidad: UC14 - Consultar panel de ocupación

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar la ocupación por espacio y franja (Prioridad: P1)

Como Administrador, quiero consultar un panel con la ocupación por espacio y franja, con filtros por fecha, disciplina y espacio, de manera que pueda conocer cómo se están usando los espacios.

**Por qué esta prioridad**: Es la herramienta del Administrador para supervisar el uso de los espacios y decidir bloqueos o ajustes de configuración.

**Prueba Independiente**: Con reservas y bloqueos en distintos espacios y fechas, consultar el panel con y sin filtros y validar que la ocupación mostrada coincide con la real.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta general de ocupación.
   - **Dado** que el Administrador está autenticado.
   - **Cuando** consulta el panel de ocupación.
   - **Entonces** el sistema muestra la ocupación por espacio y franja.

2. **Escenario**: Consulta con filtros.
   - **Dado** que el Administrador aplica filtros por fecha, disciplina y/o espacio.
   - **Cuando** el sistema actualiza el panel.
   - **Entonces** muestra solo la ocupación que cumple los filtros.

### Casos Extremos (Edge Cases)

- **¿Cómo se refleja un espacio compartido entre disciplinas al filtrar por disciplina?**
  La reserva bloquea el espacio completo sin importar la disciplina elegida (RN-07), por lo que la franja se muestra ocupada para cualquiera de sus disciplinas.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE mostrar al Administrador la ocupación por espacio y franja (RF-14).
- **RF-002**: El sistema DEBE permitir filtrar la ocupación por fecha, disciplina y espacio (RF-14).

### Requisitos No Funcionales

- **RNF-001**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-002**: El panel DEBE cumplir WCAG (RNF-05 del contexto).

### Entidades Clave

- **OcupacionFranja (DTO)**: Resultado de la consulta. Contiene espacio, fecha, franja y su ocupación. No se persiste; se obtiene de las Reservas (SPEC 003) y los Bloqueos (SPEC 010) sobre los Espacios (SPEC 011).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Exactitud, "100% de la ocupación mostrada coincide con las reservas y los bloqueos registrados".
- **CE-002**: Filtrado, "100% de los resultados filtrados cumplen los filtros de fecha, disciplina y espacio aplicados".
