# Especificación de Funcionalidad: UC05 - Confirmar asistencia (QR)

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registrar el check-in escaneando el QR del espacio (Prioridad: P1)

Como Usuario con una reserva, quiero escanear con mi celular el código QR del espacio al ingresar, de manera que el sistema confirme mi asistencia sin intervención de vigilantes ni del administrador y la reserva pase a "En uso".

**Por qué esta prioridad**: El check-in evita que la reserva se marque como inasistencia (RN-37) y es el único mecanismo de confirmación de asistencia definido.

**Prueba Independiente**: Escanear el QR de un espacio con distintas combinaciones de sesión, reserva y momento (dentro y fuera de la ventana), y validar que solo el caso válido pasa la reserva a "En uso" y que los demás muestran su motivo.

**Escenarios de Aceptación**:

1. **Escenario**: Check-in exitoso.
   - **Dado** que el Usuario está autenticado, tiene una reserva activa en ese espacio y el momento está dentro de la ventana de check-in.
   - **Cuando** escanea el QR del espacio.
   - **Entonces** el sistema registra el check-in y la reserva pasa a "En uso".

2. **Escenario**: Escaneo sin sesión iniciada.
   - **Dado** que el Usuario no tiene sesión iniciada.
   - **Cuando** escanea el QR.
   - **Entonces** el sistema le pide iniciar sesión y continúa con el check-in.

3. **Escenario**: Check-in fallido con motivo.
   - **Dado** que el Usuario no tiene reserva en ese espacio, es demasiado temprano, la ventana ya cerró o la reserva fue cancelada.
   - **Cuando** escanea el QR.
   - **Entonces** el sistema rechaza el check-in y muestra el motivo correspondiente.

4. **Escenario**: Check-in de una reserva hecha dentro de la tolerancia.
   - **Dado** que la reserva fue creada dentro de la tolerancia de la franja.
   - **Cuando** el Usuario escanea el QR dentro de los 10 minutos siguientes a la creación de la reserva.
   - **Entonces** el sistema registra el check-in y la reserva pasa a "En uso".

**Regla de la ventana de check-in** (RN-36, RN-30):

- Reserva general: desde 10 minutos antes del inicio hasta el fin de la tolerancia (10 minutos después del inicio).
- Reserva creada dentro de la tolerancia: dentro de los 10 minutos siguientes a su creación.

---

### Historia de Usuario 2 - Generar e imprimir el QR de cada espacio (Prioridad: P1)

Como Administrador, quiero generar e imprimir el código QR de cada espacio, de manera que quede disponible en su entrada para que los Usuarios confirmen su asistencia.

**Por qué esta prioridad**: Sin el QR impreso en cada espacio no es posible el check-in de la Historia de Usuario 1.

**Prueba Independiente**: Generar el QR de un espacio y validar que contiene el enlace fijo de check-in de ese espacio y que puede imprimirse.

**Escenarios de Aceptación**:

1. **Escenario**: Generación del QR de un espacio.
   - **Dado** que existe un espacio registrado y el Administrador está autenticado.
   - **Cuando** solicita generar su QR.
   - **Entonces** el sistema genera un QR estático con el enlace fijo de check-in de ese espacio (por ejemplo, `/checkin/{id_espacio}`).

2. **Escenario**: Impresión del QR.
   - **Dado** que el QR fue generado.
   - **Cuando** el Administrador solicita imprimirlo.
   - **Entonces** el sistema lo entrega listo para imprimirse y colocarse en la entrada del espacio.

### Casos Extremos (Edge Cases)

- **¿Qué contiene el QR y cómo se identifica al Usuario?**
  El QR es estático y contiene un enlace fijo al espacio (por ejemplo, `/checkin/{id_espacio}`). No identifica al Usuario; la identidad sale de su sesión iniciada (RN-31, RN-32).

- **¿Cada espacio tiene su propio QR?**
  Sí. Cada espacio tiene un código QR estático impreso en su entrada (RN-31).

- **¿Quién debe hacer el check-in?**
  Quien creó la reserva, ya que es nominal (RN-04).

- **¿Qué sucede si no se hace el check-in al cerrar la ventana?**
  La reserva pasa a "Inasistencia" y sus horas cuentan contra la cuota (RN-37; ver SPEC 010).

- **¿Qué ocurre con una reserva con check-in registrado?**
  No se puede cancelar (RN-24). Al terminar la franja con asistencia, pasa de "En uso" a "Completada" (sección 6 del contexto).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Usuario confirmar su asistencia escaneando el QR del espacio al ingresar, sin intervención de vigilantes ni del administrador (RF-08, RN-31).
- **RF-002**: El sistema DEBE comprobar, al escanear, que el Usuario esté autenticado, que tenga una reserva activa en ese espacio y que el momento esté dentro de la ventana de check-in; si todo coincide, la reserva pasa a "En uso" (RN-33).
- **RF-003**: El sistema DEBE pedir el inicio de sesión cuando no haya una sesión activa y continuar con el check-in una vez autenticado el Usuario (RN-34).
- **RF-004**: El sistema DEBE mostrar el motivo cuando el check-in falle: no tiene reserva en este espacio, es demasiado temprano, la ventana ya cerró o la reserva fue cancelada (RN-35).
- **RF-005**: El sistema DEBE aplicar la ventana de check-in desde 10 minutos antes del inicio hasta el fin de la tolerancia (RN-36) y, para reservas creadas dentro de la tolerancia, el plazo de 10 minutos desde su creación (RN-30).
- **RF-006**: El sistema DEBE permitir al Administrador generar el QR de cada espacio (RF-16).
- **RF-007**: El sistema DEBE generar el QR como un código estático que contiene un enlace fijo al espacio, sin identificar al Usuario (RN-31, RN-32).
- **RF-008**: El sistema DEBE permitir imprimir el QR (RF-16).

### Requisitos No Funcionales

- **RNF-001**: El flujo de escaneo DEBE poder resolverse cómodamente desde el celular (RNF-02 del contexto).
- **RNF-002**: El sistema DEBE registrar auditoría de los check-ins (RNF-08 del contexto).
- **RNF-003**: Las fechas y horas DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).
- **RNF-004**: La generación del QR DEBE cumplir WCAG (RNF-05 del contexto).

### Entidades Clave

- **Reserva**: Entidad definida en la SPEC 003. El check-in la pasa a estado "En uso".
- **Espacio**: Entidad definida en la SPEC 007. Cada espacio tiene un QR estático que se deriva de su identificador (enlace fijo `/checkin/{id_espacio}`); el QR no se modela como entidad independiente.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cumplimiento de Reglas, "100% de los check-in fuera de la ventana, sin reserva en el espacio o con la reserva cancelada son rechazados con su motivo".
- **CE-002**: Autonomía, "0 check-in que requieran intervención de un vigilante o del administrador".
- **CE-003**: Correspondencia, "100% de los QR generados dirigen al enlace de check-in del espacio correcto".
- **CE-004**: Privacidad, "0 QR que contengan datos de un Usuario".
