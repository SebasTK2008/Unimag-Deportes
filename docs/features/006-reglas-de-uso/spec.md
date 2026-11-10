# Especificación de Funcionalidad: UC06 - Reglas de uso

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar las reglas de uso en lenguaje claro (Prioridad: P1)

Como Usuario, quiero consultar las reglas de uso (cuota, tolerancia, cancelación, check-in y sanciones) en lenguaje claro, de manera que conozca las condiciones de las reservas antes de hacerlas.

**Por qué esta prioridad**: Las reglas se aplican automáticamente a todos los Usuarios sin intervención del administrador; el Usuario necesita conocerlas para evitar rechazos, cancelaciones tardías e inasistencias.

**Prueba Independiente**: Consultar las reglas de uso, validar que se muestran las cinco categorías con los valores vigentes y que un cambio de parámetros hecho por el Administrador (Historia de Usuario 2) se refleja en la consulta.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de las reglas vigentes.
   - **Dado** que el Usuario está en la plataforma.
   - **Cuando** consulta las reglas de uso.
   - **Entonces** el sistema muestra, en lenguaje claro, las reglas de cuota, tolerancia, cancelación, check-in y sanciones con sus valores vigentes.

2. **Escenario**: Reflejo de cambios de configuración.
   - **Dado** que el Administrador modificó un parámetro.
   - **Cuando** el Usuario consulta las reglas de uso.
   - **Entonces** el sistema muestra el valor actualizado.

---

### Historia de Usuario 2 - Editar los parámetros de las reglas de uso desde la configuración (Prioridad: P1)

Como Administrador, quiero editar desde la configuración los parámetros de uso de la plataforma (cuota, tolerancia, cancelación, anticipación, check-in, recordatorio, suspensión y dominio de correo), de manera que las reglas puedan cambiar sin modificar el código.

**Por qué esta prioridad**: Todas las demás funcionalidades (reservas, cancelaciones, check-in, sanciones, notificaciones) dependen de estos valores, que según el contexto no deben quedar fijos en el código.

**Prueba Independiente**: Modificar un parámetro (por ejemplo, la cuota semanal) y validar que la plataforma aplica el nuevo valor en las validaciones posteriores y que la consulta de la Historia de Usuario 1 lo refleja.

**Escenarios de Aceptación**:

1. **Escenario**: Modificación de un parámetro.
   - **Dado** que el Administrador está autenticado.
   - **Cuando** edita un parámetro de la configuración y guarda.
   - **Entonces** el sistema aplica el nuevo valor en las validaciones posteriores.

2. **Escenario**: Valores por defecto.
   - **Dado** que no se ha modificado ningún parámetro.
   - **Cuando** el sistema aplica las reglas.
   - **Entonces** utiliza los valores por defecto de la sección 4 del contexto (ver "Entidades Clave").

3. **Escenario**: Reflejo para los Usuarios.
   - **Dado** que el Administrador guardó un cambio.
   - **Cuando** un Usuario consulta las reglas de uso.
   - **Entonces** ve el valor actualizado.

### Casos Extremos (Edge Cases)

- **¿De dónde provienen los valores que se muestran?**
  De los parámetros configurables de la sección 4 del contexto (Historia de Usuario 2), que no están fijos en el código. Los valores por defecto son: tolerancia de 10 minutos, cancelación tardía con menos de 60 minutos, cuota de 4 horas por semana, y suspensión de 7 días por 3 inasistencias en 30 días.

- **¿Hay multas por incumplir las reglas?**
  No. La sanción es perder las horas y, de repetirse, una suspensión temporal (sección 5.8 del contexto).

- **¿Qué ocurre con las reservas ya existentes cuando cambia un parámetro?**
  **[Pendiente de definir]** El contexto no establece si los cambios afectan solo a las reservas nuevas o también a las existentes.

- **¿Los cobros monetarios son un parámetro editable?**
  No. La tabla de parámetros lo lista con valor "Ninguno" y el principio 1 del contexto define que no hay cobros monetarios; esta SPEC no incluye cobros como parámetro editable.

- **¿Quién puede editar los parámetros?**
  El Administrador, personal del área de deportes (sección 2 del contexto).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario consultar las reglas de uso en lenguaje claro (RF-09).
- **RF-002**: El sistema DEBE cubrir al menos las reglas de cuota, tolerancia, cancelación, check-in y sanciones (RF-09).
- **RF-003**: El sistema DEBE mostrar los valores vigentes de los parámetros configurados, sin valores fijos en el código (sección 4 del contexto).
- **RF-004**: El sistema DEBE permitir al Administrador editar los parámetros de la sección 4 del contexto (RF-12).
- **RF-005**: El sistema DEBE almacenar los parámetros como configuración editable y NO DEBE dejarlos fijos en el código (sección 4 del contexto).
- **RF-006**: El sistema DEBE aplicar los valores configurados en las validaciones y reglas de los demás casos de uso.
- **RF-007**: El sistema DEBE usar los valores por defecto de la sección 4 mientras no se modifiquen.

### Requisitos No Funcionales

- **RNF-001**: La consulta DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-002**: La consulta y la configuración DEBEN cumplir WCAG (RNF-05 del contexto).
- **RNF-003**: El inicio de la semana de conteo DEBE expresarse en zona horaria America/Bogota (sección 4 y RNF-07 del contexto).

### Entidades Clave

- **ParametrosDeUso**: Conjunto de valores configurables de la plataforma. La Historia de Usuario 1 los consulta y la Historia de Usuario 2 los edita. Atributos y valores por defecto:
  - Tolerancia para reservar una vez iniciada la franja: 10 minutos.
  - Umbral de cancelación tardía: menos de 60 minutos antes del inicio.
  - Duración de una franja: 60 minutos.
  - Duración máxima de una reserva: 2 franjas consecutivas (2 horas).
  - Cuota semanal por usuario: 4 horas por semana.
  - Inicio de la semana de conteo: lunes 00:00, zona horaria America/Bogota.
  - Anticipación mínima para reservar: 0 minutos (se permite hasta el fin de la tolerancia).
  - Anticipación máxima para reservar: 7 días.
  - Ventana de check-in: desde 10 minutos antes del inicio hasta el fin de la tolerancia.
  - Plazo de check-in en reservas hechas dentro de la tolerancia: 10 minutos desde su creación.
  - Recordatorio previo a la reserva: 2 horas antes del inicio.
  - Suspensión por inasistencias: 3 inasistencias en 30 días suspenden 7 días.
  - Dominio de correo permitido: `@unimagdalena.edu.co` (dominio exacto por confirmar).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Coherencia, "100% de los valores mostrados coinciden con los parámetros vigentes configurados".
- **CE-002**: Cobertura, "Las cinco categorías de reglas (cuota, tolerancia, cancelación, check-in y sanciones) están presentes en la consulta".
- **CE-003**: Configurabilidad, "100% de los parámetros de la sección 4 son editables desde la configuración, sin cambios en el código".
- **CE-004**: Aplicación de Cambios, "100% de los cambios guardados se reflejan en las validaciones posteriores y en la consulta de reglas de uso".
