# Especificación de Funcionalidad: UC06 - Reglas de uso

**Creado**: 2026-10-13  
**Última revisión**: 2026-11-10 (cierre de OQ-05, OQ-11 y OQ-16)

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

Como Administrador, quiero editar la cuota semanal y el umbral de inasistencias, de manera que esas reglas puedan cambiar sin modificar el código.

**Por qué esta prioridad**: Las reservas y sanciones dependen de estos valores; los demás parámetros son fijos o se configuran por despliegue según la sección 4 del contexto.

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
  De los valores fijos, de despliegue y de administración de la sección 4 del contexto. Valores iniciales: tolerancia de 10 minutos, cancelación tardía con menos de 60 minutos, cuota de 4 horas por semana y suspensión de 7 días por 3 inasistencias en el mes calendario.

- **¿Hay multas por incumplir las reglas?**
  No. La sanción es perder las horas y, de repetirse, una suspensión temporal (sección 5.8 del contexto).

- **¿Qué ocurre con las reservas ya existentes cuando cambia un parámetro?**
  Los cambios rigen solo para operaciones nuevas; no modifican reservas existentes (OQ-05).

- **¿Los cobros monetarios son un parámetro editable?**
  No. La tabla de parámetros lo lista con valor "Ninguno" y el principio 1 del contexto define que no hay cobros monetarios; esta SPEC no incluye cobros como parámetro editable.

- **¿Quién puede editar los parámetros?**
  El Administrador edita únicamente la cuota y el umbral de inasistencias; los valores de despliegue los configura el operador (sección 4 del contexto).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario consultar las reglas de uso en lenguaje claro (RF-09).
- **RF-002**: El sistema DEBE cubrir al menos las reglas de cuota, tolerancia, cancelación, check-in y sanciones (RF-09).
- **RF-003**: El sistema DEBE mostrar los valores vigentes de las reglas (sección 4 del contexto).
- **RF-004**: El sistema DEBE permitir al Administrador editar únicamente la cuota semanal y el umbral de inasistencias (RF-12).
- **RF-005**: El sistema DEBE almacenar esos dos valores editables; los restantes son fijos o configurables por despliegue según la sección 4.
- **RF-006**: El sistema DEBE aplicar los valores vigentes a reservas nuevas y conservar las reglas de las reservas existentes.
- **RF-007**: El sistema DEBE usar los valores por defecto de la sección 4 mientras no se modifiquen.
- **RF-008**: Cada cambio efectivo DEBE crear una versión inmutable con cuota, umbral, UUID, número secuencial, fecha y administrador responsable, conservando las versiones anteriores. La versión inicial utiliza los valores por defecto y no tiene autor administrativo.
- **RF-009**: La publicación de una versión, su activación y su auditoría DEBEN ser atómicas. Las actualizaciones parciales concurrentes DEBEN conservar los campos omitidos de la última versión vigente; una solicitud sin cambios efectivos no crea versión ni auditoría de modificación.

### Requisitos No Funcionales

- **RNF-001**: La consulta DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-002**: La consulta y la configuración DEBEN cumplir WCAG (RNF-05 del contexto).
- **RNF-003**: El inicio de la semana de conteo DEBE expresarse en zona horaria America/Bogota (sección 4 y RNF-07 del contexto).

### Entidades Clave

- **VersionDeParametros**: Instantánea inmutable de cuota y umbral con `id`, `versionNumber`, `createdAt` y `createdBy`. La numeración ordena el historial; la fecha indica el registro, no una vigencia programada.
- **ParametrosVigentes**: Referencia única a la versión activa. Las consultas actuales devuelven sus valores sin añadir campos ni endpoints de historial al contrato REST.

- **ParametrosDeUso**: Valores de las reglas consultados en la Historia 1; la Historia 2 edita solo cuota y umbral de inasistencias. Valores iniciales:
  - Tolerancia para reservar una vez iniciada la franja: 10 minutos.
  - Umbral de cancelación tardía: menos de 60 minutos antes del inicio.
  - Duración de una franja: 60 minutos.
  - Duración máxima de una reserva: 2 franjas consecutivas (2 horas).
  - Cuota semanal por usuario: 4 horas por semana.
  - Inicio de la semana de conteo: lunes 00:00, zona horaria America/Bogota.
  - Anticipación mínima para reservar: 0 minutos (se permite hasta el fin de la tolerancia).
  - Anticipación máxima para reservar: 7 días.
  - Ventana de check-in: desde la apertura configurada (10 minutos antes por defecto) hasta el fin de la reserva, sin incluir ese instante.
  - Recordatorio previo a la reserva: 2 horas antes del inicio.
  - Suspensión por inasistencias: 3 inasistencias en el mes calendario suspenden 7 días por defecto.
  - Dominio de correo permitido: `@unimagdalena.edu.co` (OQ-11).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Coherencia, "100% de los valores mostrados coinciden con los parámetros vigentes configurados".
- **CE-002**: Cobertura, "Las cinco categorías de reglas (cuota, tolerancia, cancelación, check-in y sanciones) están presentes en la consulta".
- **CE-003**: Configurabilidad, "La cuota semanal y el umbral de inasistencias son editables por Administrador; los demás valores respetan la clasificación de la sección 4".
- **CE-004**: Aplicación de Cambios, "100% de los cambios guardados se reflejan en las validaciones posteriores y en la consulta de reglas de uso".
- **CE-005**: Historial, "Cada cambio efectivo conserva una nueva versión completa y sus metadatos; ninguna versión anterior se sobrescribe o elimina mediante actualizaciones de parámetros".
- **CE-006**: Atomicidad, "Los cambios concurrentes preservan los valores omitidos; un fallo revierte versión, activación y auditoría, y una solicitud sin cambios no añade versiones".
