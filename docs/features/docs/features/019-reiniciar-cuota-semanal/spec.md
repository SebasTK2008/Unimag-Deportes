# Especificación de Funcionalidad: UC19 - Reiniciar cuota semanal

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reiniciar la cuota de cada Usuario cada semana (Prioridad: P1)

Como el sistema, al comenzar cada semana de conteo, quiero reiniciar la cuota semanal de los Usuarios al límite configurado, de manera que cada Usuario cuente con sus horas completas en la nueva semana.

**Por qué esta prioridad**: La cuota semanal es el único mecanismo de control de uso; su reinicio periódico mantiene el funcionamiento de las reservas.

**Prueba Independiente**: Con Usuarios con horas contadas en una semana, validar que al iniciar la semana siguiente (lunes 00:00, zona horaria America/Bogota) la cuota vuelve al límite configurado, considerando las reservas que ya estén hechas para esa semana.

**Escenarios de Aceptación**:

1. **Escenario**: Inicio de una nueva semana.
   - **Dado** que comienza una nueva semana de conteo.
   - **Cuando** llega el lunes a las 00:00 en zona horaria America/Bogota.
   - **Entonces** la cuota semanal de los Usuarios vuelve al límite configurado (4 horas por defecto).

2. **Escenario**: Reservas ya hechas para la semana nueva.
   - **Dado** que un Usuario hizo una reserva para una fecha de la semana siguiente.
   - **Cuando** comienza esa semana.
   - **Entonces** la reserva cuenta en la cuota de esa semana, ya que la semana de una reserva es la de la fecha de la franja.

### Casos Extremos (Edge Cases)

- **¿Cuándo comienza la semana de conteo?**
  El lunes a las 00:00, zona horaria America/Bogota (sección 4 del contexto), valor configurable (ver SPEC 009).

- **¿En qué semana cuenta una reserva creada con anticipación?**
  En la de la fecha de la franja, no en la de creación (RN-17).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE reiniciar la cuota semanal cada semana al límite configurado (RF-21).
- **RF-002**: El sistema DEBE iniciar la semana de conteo el lunes a las 00:00 en zona horaria America/Bogota (sección 4 del contexto).
- **RF-003**: El sistema DEBE contar cada reserva en la semana de la fecha de la franja (RN-17).

### Requisitos No Funcionales

- **RNF-001**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **CuotaSemanal**: Concepto definido en la SPEC 012. Esta función la reinicia al límite configurado; no se modela como entidad independiente.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Reinicio Correcto, "100% de las cuotas semanales vuelven al límite configurado al iniciar la semana, descontando las reservas ya hechas para esa semana".
