# Especificación de Funcionalidad: UC01 - Iniciar sesión

**Creado**: 2026-10-13  
**Última revisión**: 2026-10-14 (acceso con correo institucional y código de un solo uso; sesión con token JWT de 24 horas, sin cookies)

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Acceder a la plataforma con correo institucional y código de acceso (Prioridad: P1)

Como Usuario o Administrador, quiero iniciar sesión ingresando mi correo institucional y un código de un solo uso que recibo en ese correo, sin contraseña, de manera que el sistema valide que pertenezco a la universidad y me dé acceso a las funcionalidades de la plataforma.

**Por qué esta prioridad**: Es la puerta de entrada de la plataforma. Reservar, cancelar, confirmar asistencia y administrar la configuración requieren una identidad autenticada, y las reservas son nominales (RN-03). El código enviado al correo demuestra que la persona controla un buzón del dominio institucional, sin necesidad de contraseñas ni de un registro previo (RN-49).

**Prueba Independiente**: Solicitar un código con un correo del dominio institucional y con uno de otro dominio; ingresar códigos correctos, incorrectos, vencidos y ya usados; y validar que solo el código correcto y vigente da acceso, que los correos de otros dominios no reciben ningún mensaje y que un Usuario nuevo debe registrar su nombre.

**Escenarios de Aceptación**:

1. **Escenario**: Solicitud del código con correo institucional.
   - **Dado** que la persona tiene un correo del dominio institucional.
   - **Cuando** ingresa su correo y solicita el código de acceso.
   - **Entonces** el sistema valida el dominio, genera un código de 6 dígitos de un solo uso, lo envía a ese correo (ver SPEC 011) y le pide ingresarlo.

2. **Escenario**: Correo de otro dominio.
   - **Dado** que la persona intenta solicitar el código con un correo que no pertenece al dominio institucional.
   - **Cuando** el sistema valida el dominio.
   - **Entonces** el sistema rechaza la solicitud y no envía ningún correo.

3. **Escenario**: Código correcto.
   - **Dado** que la persona solicitó un código y este está vigente (menos de 10 minutos), sin usar y dentro del límite de intentos.
   - **Cuando** lo ingresa.
   - **Entonces** el sistema le da acceso a la plataforma y le entrega un token de sesión con vigencia de 24 horas (ver Historia de Usuario 2).

4. **Escenario**: Código incorrecto.
   - **Dado** que la persona ingresa un código que no coincide con el enviado.
   - **Cuando** el sistema lo valida.
   - **Entonces** rechaza el acceso y cuenta el intento fallido; al llegar a 5 intentos fallidos el código se invalida y debe solicitar uno nuevo.

5. **Escenario**: Código vencido o ya usado.
   - **Dado** que el código tiene más de 10 minutos, ya fue usado o fue reemplazado por uno más reciente.
   - **Cuando** la persona lo ingresa.
   - **Entonces** el sistema lo rechaza e indica que debe solicitar un código nuevo.

6. **Escenario**: Solicitud de un código nuevo.
   - **Dado** que la persona ya tiene un código pendiente y desea otro.
   - **Cuando** solicita un código nuevo dentro de los límites permitidos.
   - **Entonces** el sistema envía el nuevo código y el anterior queda invalidado.

7. **Escenario**: Límite de solicitudes de código.
   - **Dado** que han pasado menos de 60 segundos desde la última solicitud para ese correo, o ya se hicieron 5 solicitudes en la última hora.
   - **Cuando** la persona solicita otro código.
   - **Entonces** el sistema rechaza la solicitud e informa cuánto debe esperar.

8. **Escenario**: Primer inicio de sesión.
   - **Dado** que la persona completa por primera vez la verificación del código con su correo institucional.
   - **Cuando** el sistema no encuentra un Usuario con ese correo.
   - **Entonces** el sistema crea el Usuario automáticamente y le pide su nombre, necesario para el comprobante de reserva (ver SPEC 011).

9. **Escenario**: Inicio de sesión al escanear el QR de un espacio sin sesión activa.
   - **Dado** que la persona escanea el QR de un espacio sin sesión vigente (sin token o con el token vencido).
   - **Cuando** el sistema detecta que no está autenticada.
   - **Entonces** el sistema le pide iniciar sesión con su correo y un código de acceso y, una vez autenticada, continúa con el check-in en ese mismo espacio (ver SPEC 005).

---

### Historia de Usuario 2 - Mantener, renovar y cerrar la sesión (Prioridad: P1)

Como Usuario o Administrador, quiero que mi sesión se mantenga durante 24 horas después de iniciar sesión y poder cerrarla cuando lo desee, de manera que no tenga que pedir un código en cada acción y pueda proteger mi cuenta en un dispositivo que no es mío.

**Por qué esta prioridad**: Sin una sesión que se mantenga, el Usuario tendría que pedir un código en cada reserva, lo que rompe la meta de reservar en 5 pasos o menos y en 40 segundos (RNF-01 y RNF-03 del contexto, medidos con sesión ya iniciada).

**Prueba Independiente**: Iniciar sesión, realizar acciones con el token vigente, simular el paso de 24 horas, cerrar sesión y cambiar el estado o el rol del Usuario, y validar que el sistema acepta el token solo mientras está vigente y que aplica siempre el estado actual del Usuario.

**Escenarios de Aceptación**:

1. **Escenario**: Sesión vigente.
   - **Dado** que el Usuario inició sesión hace menos de 24 horas.
   - **Cuando** realiza una acción en la plataforma.
   - **Entonces** el sistema reconoce su identidad con el token de sesión y no le pide un nuevo código.

2. **Escenario**: Sesión expirada.
   - **Dado** que han pasado 24 horas desde la emisión del token.
   - **Cuando** el Usuario intenta realizar una acción, por ejemplo reservar.
   - **Entonces** el sistema le pide iniciar sesión de nuevo con un código nuevo y, al terminar, lo devuelve a la pantalla en la que estaba. El token no se renueva automáticamente.

3. **Escenario**: Cierre de sesión.
   - **Dado** que el Usuario tiene una sesión iniciada.
   - **Cuando** cierra sesión.
   - **Entonces** el token se elimina del dispositivo y para volver a usar la plataforma debe iniciar sesión de nuevo.

4. **Escenario**: Sesiones en varios dispositivos.
   - **Dado** que el Usuario inició sesión en su celular y en otro dispositivo.
   - **Cuando** usa la plataforma en cualquiera de los dos.
   - **Entonces** ambas sesiones funcionan a la vez; cerrar sesión en uno no afecta al otro.

5. **Escenario**: Estado vigente del Usuario en cada petición.
   - **Dado** que el Usuario tiene un token vigente y, después de iniciar sesión, fue suspendido o cambió su rol.
   - **Cuando** realiza una nueva petición.
   - **Entonces** el sistema aplica su estado actual (suspensión, rol) sin esperar a que el token expire, porque el token solo identifica al Usuario.

6. **Escenario**: Token alterado o inválido.
   - **Dado** que una petición llega con un token alterado, mal formado o no emitido por el sistema.
   - **Cuando** el sistema lo verifica.
   - **Entonces** rechaza la petición y pide iniciar sesión.

### Casos Extremos (Edge Cases)

- **¿Las reglas de acceso son distintas para estudiantes, docentes y funcionarios?**
  No. Los tres tienen las mismas reglas y la misma cuota (RN-02).

- **¿Puede iniciar sesión un Usuario suspendido?**
  Sí. Un Usuario suspendido puede consultar disponibilidad y sus reservas, pero no puede crear nuevas hasta que termine la suspensión (RN-05).

- **¿Cuál es el dominio institucional válido?**
  El contexto indica `@unimagdalena.edu.co` y señala que el dominio exacto debe confirmarse. El dominio es un parámetro configurable (ver SPEC 006), no un valor fijo en el código.

- **¿Cómo se identifica a un Administrador?**
  El Administrador se autentica con el mismo mecanismo que el Usuario (sección 2 del contexto). Su rol no se obtiene por autoservicio: se asigna a correos institucionales específicos desde la configuración del sistema (RN-60).

- **¿Hace falta registrarse antes de iniciar sesión?**
  No. No existen contraseñas ni registro previo (RN-49); el Usuario se crea automáticamente en su primer inicio de sesión y solo se le pide su nombre (RN-59).

- **¿Qué ocurre si el código no llega al correo?**
  El Usuario puede solicitar un código nuevo respetando los límites de solicitud (RN-53); el envío y la entrega se especifican en la SPEC 011.

- **¿Qué ocurre si el enlace del QR se abre en un navegador distinto al de la sesión?**
  Como el token no se guarda en cookies, el Usuario deberá iniciar sesión de nuevo en ese navegador antes de que se registre su check-in (RN-34).

- **¿Dónde se guarda el token y qué contiene?**
  No se guarda en cookies: el cliente lo conserva en el almacenamiento del navegador y lo envía en cada petición (RN-55). Contiene como mínimo el identificador del Usuario, su correo, su rol y las fechas de emisión y expiración (RN-54).

- **¿Se puede invalidar un token antes de las 24 horas?**
  **[Pendiente de definir]** Cerrar sesión elimina el token del dispositivo (RN-57), pero el contexto no define una revocación en el servidor para, por ejemplo, un dispositivo perdido. Mientras tanto, la suspensión y el cambio de rol sí aplican de inmediato (RN-58).

- **¿Los valores del código y de los límites son definitivos?**
  Algunos están marcados como propuestos en el contexto y por confirmar: longitud del código (6 dígitos), vigencia (10 minutos), intentos fallidos (5) y límites de reenvío (60 segundos entre solicitudes y 5 por hora por correo) (RN-51 a RN-53). No deben quedar fijos en el código.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir iniciar sesión con el correo institucional y un código de acceso de un solo uso enviado a ese correo, sin contraseñas ni registro previo (RF-01, RN-01, RN-49).
- **RF-002**: El sistema DEBE validar el dominio del correo al solicitar el código y, si no pertenece al dominio institucional configurado, rechazar la solicitud sin enviar ningún correo (RF-01, RN-50).
- **RF-003**: El sistema DEBE generar el código de acceso con 6 dígitos, con un generador criptográficamente seguro, con vigencia de 10 minutos y de un solo uso, y DEBE invalidar el código anterior cuando se solicite uno nuevo (RN-51).
- **RF-004**: El sistema DEBE guardar únicamente el hash del código de acceso, nunca el código en claro (RN-51, RNF-09 del contexto).
- **RF-005**: El sistema DEBE enviar el código al correo institucional mediante el Servicio de correo (RF-20, RN-49; ver SPEC 011).
- **RF-006**: El sistema DEBE admitir un máximo de 5 intentos fallidos por código e invalidarlo al superarlos (RN-52).
- **RF-007**: El sistema DEBE exigir al menos 60 segundos entre dos solicitudes de código para el mismo correo, permitir como máximo 5 solicitudes por hora por correo e informar al Usuario cuánto debe esperar (RN-53).
- **RF-008**: El sistema DEBE emitir, ante un código correcto, un token JWT firmado con vigencia de 24 horas desde su emisión, que incluya como mínimo el identificador del Usuario, su correo, su rol y las fechas de emisión y expiración (RN-54).
- **RF-009**: El sistema NO DEBE guardar el token en cookies: el cliente lo conserva y lo envía en cada petición en el encabezado `Authorization: Bearer` (RN-55).
- **RF-010**: El sistema NO DEBE renovar el token automáticamente; al expirar, DEBE pedir iniciar sesión de nuevo y devolver al Usuario a la pantalla en la que estaba (RN-56).
- **RF-011**: El sistema DEBE permitir cerrar sesión, eliminando el token del dispositivo, y permitir sesiones abiertas en varios dispositivos a la vez (RN-57).
- **RF-012**: El sistema DEBE consultar en cada petición el estado vigente del Usuario (existencia, rol y suspensión) y NO DEBE deducirlo del token (RN-58).
- **RF-013**: El sistema DEBE crear automáticamente al Usuario en su primer inicio de sesión y pedirle su nombre (RN-59).
- **RF-014**: El sistema DEBE asignar el rol de Administrador solo a correos institucionales específicos definidos en la configuración del sistema (RN-60).
- **RF-015**: El sistema DEBE aplicar las mismas reglas de acceso a estudiantes, docentes y funcionarios (RN-02).
- **RF-016**: El sistema DEBE permitir el inicio de sesión de un Usuario suspendido, limitando sus acciones a consultar disponibilidad y sus reservas (RN-05).
- **RF-017**: El sistema DEBE, ante un escaneo de QR sin sesión vigente, pedir el inicio de sesión con correo y código y continuar con el check-in en el mismo espacio una vez autenticada la persona (RN-34).
- **RF-018**: El sistema DEBE gestionar la autenticación: generar, enviar, validar y expirar códigos; aplicar los límites de intentos y reenvíos; y emitir y verificar los tokens (RF-22).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE guardar únicamente los datos necesarios del usuario: nombre y correo institucional; no se guardan contraseñas ni códigos de acceso en claro (RNF-09 del contexto).
- **RNF-002**: El flujo de inicio de sesión DEBE poder resolverse cómodamente desde el celular (diseño *mobile-first*, RNF-02 del contexto) y cumplir WCAG (RNF-05 del contexto).
- **RNF-003**: El sistema DEBE registrar en la auditoría cada solicitud de código, cada inicio de sesión exitoso o fallido y cada cierre de sesión (RN-61, RNF-08 del contexto).
- **RNF-004**: Toda comunicación DEBE ir por HTTPS; el token DEBE firmarse con una llave secreta que no esté en el código fuente; el servidor DEBE fijar el algoritmo de firma sin aceptarlo del cliente; y, como el token vive en el navegador, la aplicación DEBE aplicar medidas contra XSS (política de seguridad de contenido y escape de los datos mostrados) (RNF-10 del contexto).
- **RNF-005**: El correo con el código DEBE llegar en menos de 1 minuto en condiciones normales (RNF-11 del contexto; ver SPEC 011).
- **RNF-006**: Con sesión vigente, el inicio de sesión NO DEBE contar dentro de los 5 pasos ni de los 40 segundos de una reserva (RNF-01 y RNF-03 del contexto).
- **RNF-007**: Las fechas y horas (vigencia del código, emisión y expiración del token, auditoría) DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Usuario**: Persona con correo institucional que usa la plataforma. Atributos: nombre, correo institucional, rol (Usuario o Administrador) y estado de suspensión (ver SPEC 010). Se crea automáticamente en su primer inicio de sesión. Su identidad proviene de la sesión iniciada (RN-32).
- **CódigoDeAcceso**: Código numérico de 6 dígitos, de un solo uso, asociado a un correo institucional. Atributos: hash del código, momento de emisión, vigencia (10 minutos), número de intentos fallidos y estado (vigente, usado, vencido o invalidado). Solo se conserva su hash; el código en claro existe únicamente en el correo enviado al Usuario.
- **TokenDeSesión (JWT)**: Credencial firmada por el sistema, con vigencia de 24 horas, que el cliente envía en cada petición. Contiene el identificador del Usuario, su correo, su rol y las fechas de emisión y expiración. No se guarda en cookies y solo identifica al Usuario: su estado vigente se consulta en cada petición.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Control de Acceso, "100% de los intentos de inicio de sesión con correos fuera del dominio institucional son rechazados y 0 reciben un código de acceso".
- **CE-002**: Consistencia de Reglas, "0 diferencias en las reglas de acceso entre estudiantes, docentes y funcionarios".
- **CE-003**: Seguridad del Código, "100% de los códigos de acceso son de un solo uso y quedan inválidos al usarse, al cumplir 10 minutos, al superar 5 intentos fallidos o al solicitarse uno nuevo".
- **CE-004**: Límites de Solicitud, "100% de las solicitudes que incumplen el mínimo de 60 segundos o el máximo de 5 por hora son rechazadas con el tiempo de espera".
- **CE-005**: Vigencia de la Sesión, "100% de los tokens dejan de ser aceptados a las 24 horas de su emisión y 0 se renuevan automáticamente".
- **CE-006**: Estado Vigente, "100% de las peticiones aplican el estado actual del Usuario (existencia, rol y suspensión) y 0 aceptan un token alterado".
- **CE-007**: Privacidad, "0 contraseñas o códigos de acceso almacenados en claro".
- **CE-008**: Continuidad del Check-in, "100% de los escaneos de QR sin sesión vuelven al check-in del mismo espacio una vez la persona se autentica".
- **CE-009**: Trazabilidad, "100% de las solicitudes de código, inicios de sesión (exitosos y fallidos) y cierres de sesión quedan en el registro de auditoría".