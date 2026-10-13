# Especificación de Funcionalidad: UC08 - Consultar reglas de uso

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar las reglas de uso en lenguaje claro (Prioridad: P1)

Como Usuario, quiero consultar las reglas de uso (cuota, tolerancia, cancelación, check-in y sanciones) en lenguaje claro, de manera que conozca las condiciones de las reservas antes de hacerlas.

**Por qué esta prioridad**: Las reglas se aplican automáticamente a todos los Usuarios sin intervención del administrador; el Usuario necesita conocerlas para evitar rechazos, cancelaciones tardías e inasistencias.

**Prueba Independiente**: Consultar las reglas de uso, validar que se muestran las cinco categorías con los valores vigentes y que un cambio de parámetros hecho por el Administrador se refleja en la consulta.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de las reglas vigentes.
   - **Dado** que el Usuario está en la plataforma.
   - **Cuando** consulta las reglas de uso.
   - **Entonces** el sistema muestra, en lenguaje claro, las reglas de cuota, tolerancia, cancelación, check-in y sanciones con sus valores vigentes.

2. **Escenario**: Reflejo de cambios de configuración.
   - **Dado** que el Administrador modificó un parámetro mediante "Configurar reglas de uso".
   - **Cuando** el Usuario consulta las reglas de uso.
   - **Entonces** el sistema muestra el valor actualizado.

### Casos Extremos (Edge Cases)

- **¿De dónde provienen los valores que se muestran?**
  De los parámetros configurables de la sección 4 del contexto (ver SPEC 009), que no están fijos en el código. Los valores por defecto son: tolerancia de 10 minutos, cancelación tardía con menos de 60 minutos, cuota de 4 horas por semana, y suspensión de 7 días por 3 inasistencias en 30 días.

- **¿Hay multas por incumplir las reglas?**
  No. La sanción es perder las horas y, de repetirse, una suspensión temporal (sección 5.8 del contexto).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario consultar las reglas de uso en lenguaje claro (RF-09).
- **RF-002**: El sistema DEBE cubrir al menos las reglas de cuota, tolerancia, cancelación, check-in y sanciones (RF-09).
- **RF-003**: El sistema DEBE mostrar los valores vigentes de los parámetros configurados, sin valores fijos en el código (sección 4 del contexto).

### Requisitos No Funcionales

- **RNF-001**: La consulta DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto) y cumplir WCAG (RNF-05 del contexto).

### Entidades Clave

- **ParametrosDeUso**: Entidad definida en la SPEC 009. Aquí solo se consulta.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Coherencia, "100% de los valores mostrados coinciden con los parámetros vigentes configurados".
- **CE-002**: Cobertura, "Las cinco categorías de reglas (cuota, tolerancia, cancelación, check-in y sanciones) están presentes en la consulta".
