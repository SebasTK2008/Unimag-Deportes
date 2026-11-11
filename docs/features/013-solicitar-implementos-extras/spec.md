# Especificación de Funcionalidad: UC13 - Solicitar implementos extras

**Creado**: 2026-11-10  
**Última revisión**: 2026-11-10 (cierre de OQ-18 a OQ-21; historia de catálogo)

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Solicitar implementos al reservar (Prioridad: P2)

Como Usuario autenticado, quiero solicitar opcionalmente uno o varios implementos del catálogo (por ejemplo, un balón de voleibol o de fútbol) e indicar la cantidad, al momento de hacer una reserva, de manera que la reserva incluya lo que necesito para usar el espacio, siempre que haya disponibilidad en la franja.

**Por qué esta prioridad**: Complementa la reserva (UC03); un espacio puede reservarse sin implementos, pero ciertas disciplinas los requieren. Depende de que existan espacios (UC07) y reservas (UC03).

**Prueba Independiente**: Crear una reserva sin implementos y otra con uno y varios implementos con distintas cantidades, y validar que la reserva se confirma en ambos casos y que los implementos quedan asociados solo a la reserva que los solicitó. Validar además que una solicitud que supera el stock disponible en la franja se rechaza.

**Escenarios de Aceptación**:

1. **Escenario**: Reserva con un implemento.
   - **Dado** que el Usuario está autenticado, la franja está libre y existe un implemento disponible con stock suficiente para la franja.
   - **Cuando** confirma la reserva y solicita ese implemento con una cantidad.
   - **Entonces** la reserva se confirma y el implemento queda asociado a ella con la cantidad indicada.

2. **Escenario**: Reserva con varios implementos.
   - **Dado** que el Usuario está reservando y hay varios implementos disponibles.
   - **Cuando** solicita más de un implemento, cada uno con su cantidad.
   - **Entonces** el sistema registra todos los implementos solicitados en la misma reserva.

3. **Escenario**: Reserva sin implementos.
   - **Dado** que el Usuario está reservando y no necesita implementos.
   - **Cuando** confirma la reserva sin seleccionar ninguno.
   - **Entonces** la reserva se confirma normalmente y no se registra ninguna solicitud de implemento.

4. **Escenario**: Implemento inactivo o inexistente.
   - **Dado** que el Usuario intenta solicitar un implemento que no existe o está inactivo.
   - **Cuando** confirma la reserva.
   - **Entonces** el sistema rechaza la reserva e informa que el implemento no está disponible.

5. **Escenario**: Stock insuficiente en la franja.
   - **Dado** que para la franja solo quedan 2 unidades disponibles de un implemento.
   - **Cuando** el Usuario solicita 3 unidades y confirma la reserva.
   - **Entonces** el sistema rechaza la reserva e informa que solo quedan 2 unidades.

6. **Escenario**: Reservas solapadas consumen el mismo stock.
   - **Dado** que un implemento tiene 2 unidades y una reserva activa que se solapa ya solicitó las 2.
   - **Cuando** otro Usuario intenta reservar en esa franja y solicita el mismo implemento.
   - **Entonces** el sistema rechaza la solicitud por falta de disponibilidad.

### Casos Extremos (Edge Cases)

- **¿Solicitar implementos es obligatorio?**
  No. Es opcional; una reserva sin implementos se confirma igual (RN-63).

- **¿Cuántos implementos y qué cantidad puede pedir una reserva?**
  Puede pedir varios implementos, cada uno con su cantidad. No hay un límite adicional por reserva: la cantidad queda acotada por el stock disponible en la franja (OQ-18).

- **¿Cómo se calcula la disponibilidad de un implemento?**
  `stock` menos el máximo de unidades comprometidas simultáneamente por reservas activas durante el intervalo (RN-65). Reservas consecutivas no se suman entre sí. No se almacena un saldo por franja (RN-66).

- **¿Qué pasa si dos reservas se solapan en el tiempo?**
  Consumen el mismo stock: la segunda solo se confirma si todavía hay unidades disponibles para su franja.

- **¿Qué pasa con los implementos si la reserva se cancela?**
  El registro se conserva como historial y deja de contar para la disponibilidad, por lo que libera el stock (RN-66, OQ-19).

- **¿El comprobante de la reserva incluye los implementos solicitados?**
  Sí, el comprobante lista cada implemento con su cantidad (RN-45, OQ-20; ver SPEC 011).

- **¿Quién administra el catálogo?**
  El Administrador: crea, edita y desactiva implementos y define sus existencias (RF-24, RN-62).

- **¿Los implementos dependen de la disciplina del espacio?**
  No se restringen: la disciplina del implemento es solo una sugerencia para filtrar en la interfaz (OQ-21).

- **¿Qué pasa si se desactiva un implemento con reservas futuras?**
  Los cambios rigen solo para operaciones nuevas: las solicitudes existentes se mantienen y se impide solicitar el implemento de nuevo (OQ-05).

---

### Historia de Usuario 2 - Gestionar el catálogo de implementos (Prioridad: P2)

Como Administrador, quiero crear, editar y desactivar implementos con su disciplina sugerida y sus existencias, de manera que el catálogo refleje lo que el área de deportes puede prestar.

**Por qué esta prioridad**: Sin catálogo no hay implementos que solicitar; es la base de la Historia de Usuario 1.

**Prueba Independiente**: Crear un implemento con stock, editarlo y desactivarlo, y validar que solo los disponibles con stock suficiente se pueden solicitar.

**Escenarios de Aceptación**:

1. **Escenario**: Crear un implemento.
   - **Dado** que el Administrador está autenticado.
   - **Cuando** crea un implemento con nombre, disciplina sugerida y stock.
   - **Entonces** queda disponible para solicitarse en las reservas.

2. **Escenario**: Editar existencias.
   - **Dado** que existe un implemento.
   - **Cuando** el Administrador cambia su stock.
   - **Entonces** las validaciones posteriores usan el nuevo stock y las reservas existentes se mantienen (OQ-05).

3. **Escenario**: Desactivar un implemento.
   - **Dado** que existe un implemento.
   - **Cuando** el Administrador lo desactiva.
   - **Entonces** deja de poder solicitarse en reservas nuevas.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario solicitar, de forma opcional, uno o varios implementos del catálogo al confirmar una reserva, indicando la cantidad de cada uno (RF-03, RF-23, RN-63).
- **RF-002**: El sistema DEBE registrar los implementos solicitados asociados a la reserva (RN-64).
- **RF-003**: El sistema DEBE validar que los implementos solicitados existan, estén disponibles y tengan stock suficiente en la franja antes de confirmar la reserva (RN-62, RN-65).
- **RF-004**: El sistema NO DEBE exigir implementos para confirmar una reserva (RN-63).
- **RF-005**: El sistema DEBE permitir al Administrador crear, editar y desactivar implementos del catálogo, con nombre, disciplina sugerida y existencias (RF-24).
- **RF-006**: El sistema DEBE permitir consultar la disponibilidad de los implementos para una franja (RF-23, RN-65).

### Requisitos No Funcionales

- **RNF-001**: La selección de implementos DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto) y cumplir WCAG (RNF-05 del contexto).
- **RNF-002**: La selección de implementos no DEBE impedir que una reserva se complete en 5 pasos o menos (RNF-01 del contexto).

### Entidades Clave

- **Implemento**: Elemento del catálogo gestionado por el Administrador. Atributos: nombre, disciplina sugerida (opcional), existencias (stock) y estado (disponible o inactivo). Su disponibilidad por franja se deriva de las reservas (RN-65, RN-66).
- **ReservaImplemento**: Asociación entre una Reserva y un Implemento con la cantidad solicitada. Una reserva puede tener cero, una o varias.
- **Reserva**: Entidad definida en la SPEC 003; la solicitud de implementos es parte de su detalle.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Registro de implementos, "100% de las reservas con implementos solicitados tienen asociados el implemento y la cantidad indicados".
- **CE-002**: Reservas sin implementos, "100% de las reservas sin implementos se confirman sin registros de implementos".
- **CE-003**: Catálogo, "0 reservas con implementos inexistentes o inactivos".
- **CE-004**: Inventario, "0 reservas confirmadas con implementos cuya cantidad supere el stock disponible en su franja".
- **CE-005**: Cancelación, "100% de las reservas canceladas conservan el registro de implementos y liberan su stock para otras reservas".
- **CE-006**: Comprobante, "100% de los comprobantes de reservas con implementos los listan con su cantidad".
