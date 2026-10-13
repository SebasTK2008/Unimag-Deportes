# Especificación de Funcionalidad: UC09 - Configurar reglas de uso

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Editar los parámetros de las reglas de uso desde la configuración (Prioridad: P1)

Como Administrador, quiero editar desde la configuración los parámetros de uso de la plataforma (cuota, tolerancia, cancelación, anticipación, check-in, recordatorio, suspensión y dominio de correo), de manera que las reglas puedan cambiar sin modificar el código.

**Por qué esta prioridad**: Todas las demás funcionalidades (reservas, cancelaciones, check-in, sanciones, notificaciones) dependen de estos valores, que según el contexto no deben quedar fijos en el código.

**Prueba Independiente**: Modificar un parámetro (por ejemplo, la cuota semanal) y validar que la plataforma aplica el nuevo valor en las validaciones posteriores y que "Consultar reglas de uso" lo refleja.

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

- **¿Qué ocurre con las reservas ya existentes cuando cambia un parámetro?**
  **[Pendiente de definir]** El contexto no establece si los cambios afectan solo a las reservas nuevas o también a las existentes.

- **¿Los cobros monetarios son un parámetro editable?**
  No. La tabla de parámetros lo lista con valor "Ninguno" y el principio 1 del contexto define que no hay cobros monetarios; esta SPEC no incluye cobros como parámetro editable.

- **¿Quién puede editar los parámetros?**
  El Administrador, personal del área de deportes (sección 2 del contexto).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador editar los parámetros de la sección 4 del contexto (RF-12).
- **RF-002**: El sistema DEBE almacenar los parámetros como configuración editable y NO DEBE dejarlos fijos en el código (sección 4 del contexto).
- **RF-003**: El sistema DEBE aplicar los valores configurados en las validaciones y reglas de los demás casos de uso.
- **RF-004**: El sistema DEBE usar los valores por defecto de la sección 4 mientras no se modifiquen.

### Requisitos No Funcionales

- **RNF-001**: El inicio de la semana de conteo DEBE expresarse en zona horaria America/Bogota (sección 4 y RNF-07 del contexto).
- **RNF-002**: La configuración DEBE cumplir WCAG (RNF-05 del contexto).

### Entidades Clave

- **ParametrosDeUso**: Conjunto de valores configurables de la plataforma. Atributos y valores por defecto:
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

- **CE-001**: Configurabilidad, "100% de los parámetros de la sección 4 son editables desde la configuración, sin cambios en el código".
- **CE-002**: Aplicación de Cambios, "100% de los cambios guardados se reflejan en las validaciones posteriores y en 'Consultar reglas de uso'".
