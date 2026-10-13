# Especificación de Funcionalidad: UC15 - Consultar indicadores de uso

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Ver los indicadores de uso de la plataforma (Prioridad: P2)

Como Administrador, quiero consultar los indicadores de uso (reservas, cancelaciones tardías, inasistencias y usuarios suspendidos), de manera que pueda evaluar cómo se aplican las reglas de la plataforma.

**Por qué esta prioridad**: Es una funcionalidad de seguimiento; no interviene en el flujo de reserva.

**Prueba Independiente**: Con reservas, cancelaciones tardías, inasistencias y suspensiones registradas, consultar los indicadores y validar que reflejan los registros.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de indicadores.
   - **Dado** que el Administrador está autenticado y existen registros de uso.
   - **Cuando** consulta los indicadores de uso.
   - **Entonces** el sistema muestra los indicadores de reservas, cancelaciones tardías, inasistencias y usuarios suspendidos.

### Casos Extremos (Edge Cases)

- **¿Sobre qué período se calculan los indicadores?**
  **[Pendiente de definir]** El contexto no establece períodos ni filtros para esta consulta.

- **¿Qué se cuenta como cancelación tardía o inasistencia?**
  Lo definido por las reglas del contexto: cancelación tardía es la hecha con menos de 60 minutos de anticipación o con la franja ya iniciada sin check-in (RN-22), e inasistencia es la reserva sin check-in al cerrar la ventana (RN-37).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE mostrar al Administrador los indicadores de reservas, cancelaciones tardías, inasistencias y usuarios suspendidos (RF-15).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE basar los indicadores en el registro de auditoría de reservas, cancelaciones, check-ins y sanciones (RNF-08 del contexto).
- **RNF-002**: La consulta DEBE cumplir WCAG (RNF-05 del contexto).

### Entidades Clave

- **IndicadoresDeUso (DTO)**: Resultado de la consulta. Contiene el número de reservas, de cancelaciones tardías, de inasistencias y de usuarios suspendidos. No se persiste; se obtiene de los registros del sistema.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Exactitud, "100% de los indicadores mostrados coinciden con los registros de reservas, cancelaciones tardías, inasistencias y suspensiones".
