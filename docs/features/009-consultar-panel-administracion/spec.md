# Especificación de Funcionalidad: UC09 - Consultar panel de administración

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

---

### Historia de Usuario 2 - Ver los indicadores de uso de la plataforma (Prioridad: P2)

Como Administrador, quiero consultar los indicadores de uso (reservas, cancelaciones tardías, inasistencias y usuarios suspendidos), de manera que pueda evaluar cómo se aplican las reglas de la plataforma.

**Por qué esta prioridad**: Es una funcionalidad de seguimiento; no interviene en el flujo de reserva.

**Prueba Independiente**: Con reservas, cancelaciones tardías, inasistencias y suspensiones registradas, consultar los indicadores y validar que reflejan los registros.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de indicadores.
   - **Dado** que el Administrador está autenticado y existen registros de uso.
   - **Cuando** consulta los indicadores de uso.
   - **Entonces** el sistema muestra los indicadores de reservas, cancelaciones tardías, inasistencias y usuarios suspendidos.

### Casos Extremos (Edge Cases)

- **¿Cómo se refleja un espacio compartido entre disciplinas al filtrar por disciplina?**
  La reserva bloquea el espacio completo sin importar la disciplina elegida (RN-07), por lo que la franja se muestra ocupada para cualquiera de sus disciplinas.

- **¿Sobre qué período se calculan los indicadores?**
  **[Pendiente de definir]** El contexto no establece períodos ni filtros para esta consulta.

- **¿Qué se cuenta como cancelación tardía o inasistencia?**
  Lo definido por las reglas del contexto: cancelación tardía es la hecha con menos de 60 minutos de anticipación o con la franja ya iniciada sin check-in (RN-22), e inasistencia es la reserva sin check-in al cerrar la ventana (RN-37).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE mostrar al Administrador la ocupación por espacio y franja (RF-14).
- **RF-002**: El sistema DEBE permitir filtrar la ocupación por fecha, disciplina y espacio (RF-14).
- **RF-003**: El sistema DEBE mostrar al Administrador los indicadores de reservas, cancelaciones tardías, inasistencias y usuarios suspendidos (RF-15).

### Requisitos No Funcionales

- **RNF-001**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-002**: El panel y los indicadores DEBEN cumplir WCAG (RNF-05 del contexto).
- **RNF-003**: El sistema DEBE basar los indicadores en el registro de auditoría de reservas, cancelaciones, check-ins y sanciones (RNF-08 del contexto).

### Entidades Clave

- **OcupacionFranja (DTO)**: Resultado de la consulta de ocupación. Contiene espacio, fecha, franja y su ocupación. No se persiste; se obtiene de las Reservas (SPEC 003) y de los Bloqueos y Espacios (SPEC 007).
- **IndicadoresDeUso (DTO)**: Resultado de la consulta de indicadores. Contiene el número de reservas, de cancelaciones tardías, de inasistencias y de usuarios suspendidos. No se persiste; se obtiene de los registros del sistema.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Exactitud, "100% de la ocupación mostrada coincide con las reservas y los bloqueos registrados".
- **CE-002**: Filtrado, "100% de los resultados filtrados cumplen los filtros de fecha, disciplina y espacio aplicados".
- **CE-003**: Exactitud de Indicadores, "100% de los indicadores mostrados coinciden con los registros de reservas, cancelaciones tardías, inasistencias y suspensiones".
