# Especificación de Funcionalidad: UC11 - Gestionar franjas y disponibilidad de espacios

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Gestionar los espacios y su horario de operación (Prioridad: P1)

Como Administrador, quiero crear, editar y desactivar espacios, definiendo su disciplina, aforo, requisitos, horario de operación y estado, de manera que las franjas reservables y su disponibilidad reflejen la realidad de cada espacio.

**Por qué esta prioridad**: Sin espacios y horarios de operación definidos no existen franjas que reservar; es la base de la plataforma.

**Prueba Independiente**: Crear un espacio con sus atributos, editar su horario y cambiar su estado a "en mantenimiento", y validar que las franjas se generan dentro del horario de operación y que el espacio en mantenimiento no aparece como reservable.

**Escenarios de Aceptación**:

1. **Escenario**: Creación de un espacio.
   - **Dado** que el Administrador está autenticado.
   - **Cuando** crea un espacio con nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado.
   - **Entonces** el espacio queda registrado y sus franjas de 60 minutos quedan definidas dentro de su horario de operación.

2. **Escenario**: Edición de un espacio.
   - **Dado** que existe un espacio.
   - **Cuando** el Administrador edita sus atributos, por ejemplo el horario de operación.
   - **Entonces** el sistema guarda los cambios y las franjas se ajustan a la nueva configuración.

3. **Escenario**: Espacio en mantenimiento.
   - **Dado** que el Administrador cambia el estado de un espacio a "en mantenimiento".
   - **Cuando** un Usuario consulta la disponibilidad.
   - **Entonces** el espacio no aparece como reservable.

### Casos Extremos (Edge Cases)

- **¿Qué disciplinas existen?**
  Baloncesto, voleibol, fútbol 5, fútbol 11 y tenis de campo (sección 1 del contexto).

- **¿Cómo se comporta un espacio que sirve para varias disciplinas?**
  La reserva bloquea el espacio completo, sin importar la disciplina elegida (RN-07).

- **¿Qué duración tienen las franjas?**
  60 minutos dentro del horario de operación de cada espacio (RN-08); la duración es un parámetro configurable (ver SPEC 009).

- **¿Qué ocurre con las reservas existentes al desactivar un espacio o ponerlo en mantenimiento?**
  **[Pendiente de definir]** El contexto no lo establece.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador crear, editar y desactivar espacios (RF-11).
- **RF-002**: El sistema DEBE registrar de cada espacio: nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado (activo o en mantenimiento) (RN-06, RF-11).
- **RF-003**: El sistema DEBE definir las franjas de cada espacio en bloques de 60 minutos dentro de su horario de operación (RN-08).
- **RF-004**: El sistema NO DEBE mostrar como reservable un espacio en mantenimiento (RN-09).
- **RF-005**: El sistema DEBE tratar un espacio compartido entre disciplinas como un único espacio completo para la reserva (RN-07).

### Requisitos No Funcionales

- **RNF-001**: La gestión DEBE cumplir WCAG (RNF-05 del contexto).
- **RNF-002**: Los horarios de operación DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Espacio**: Cancha o área física reservable (por ejemplo, "Cancha de tenis 1"). Atributos: nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado (activo o en mantenimiento).
- **Franja**: Bloque de 60 minutos reservable de un espacio, definido dentro de su horario de operación. No se modela como entidad independiente de gestión; se deriva del Espacio y de la duración configurada.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Integridad de Espacios, "100% de los espacios registrados contienen nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado".
- **CE-002**: Consistencia de Franjas, "0 franjas reservables fuera del horario de operación de su espacio o en un espacio en mantenimiento".
