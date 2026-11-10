# Especificación de Funcionalidad: UC01 - Iniciar sesión

**Creado**: 2026-10-13  
**Última revisión**: 2026-10-18 (código de acceso de un solo uso y token JWT de 24 horas sin cookies)

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Acceder a la plataforma con correo institucional y código de acceso (Prioridad: P1)

Como Usuario o Administrador, quiero iniciar sesión con mi correo institucional y un código de un solo uso enviado a ese correo, sin contraseña, de manera que el sistema valide que pertenezco a la universidad y me dé acceso a las funcionalidades de la plataforma durante 24 horas.

**Por qué esta prioridad**: Es la puerta de entrada de la plataforma. Reservar, cancelar, confirmar asistencia y administrar la configuración requieren una identidad autenticada, y las reservas son nominales (RN-03).

**Prueba Independiente**: Solicitar un código con un correo del dominio institucional y con uno de otro dominio; verificar un código correcto, uno incorrecto y uno vencido; y validar que el acceso correcto entrega un token de 24 horas y que, al expirar, se pide iniciar sesión de nuevo.

**Escenarios de Aceptación**:

1. **Escenario**: Solicitud de código con correo institucional.
   - **Dado** que la persona tiene un correo del dominio institucional.
   - **Cuando** solicita su código de acceso.
   - **Entonces** el sistema envía a ese correo un código de 6 dígitos, de un solo uso y con 10 minutos de vigencia. Si ya había un código vigente, el nuevo lo invalida.

2. **Escenario**: Correo de otro dominio.
   - **Dado** que la persona intenta solicitar el código con un correo que no pertenece al dominio institucional.
   - **Cuando** el sistema valida el dominio.
   - **Entonces** el sistema rechaza la solicitud y no envía ningún correo.

3. **Escenario**: Inicio de sesión con código correcto.
   - **Dado** que la persona recibió un código vigente y sin usar.
   - **Cuando** lo ingresa.
   - **Entonces** el sistema marca el código como usado, emite un token firmado con vigencia de 24 horas y le da acceso. El token no se guarda en cookies: el cliente lo conserva en el almacenamiento del navegador y lo envía en cada petición.

4. **Escenario**: Código incorrecto, vencido o ya usado.
   - **Dado** que la persona ingresa un código que no coincide, que venció o que ya se usó.
   - **Cuando** el sistema lo verifica.
   - **Entonces** el sistema rechaza el acceso e informa que debe corregir el código o solicitar uno nuevo.

5. **Escenario**: Límite de intentos por código.
   - **Dado** que la persona acumuló 5 intentos fallidos con el mismo código.
   - **Cuando** intenta un sexto intento.
   - **Entonces** el código queda invalidado y la persona debe solicitar uno nuevo.

6. **Escenario**: Límite de solicitudes de código.
   - **Dado** que la persona solicitó un código hace menos de 60 segundos, o ya hizo 5 solicitudes en la última hora con ese correo.
   - **Cuando** solicita otro.
   - **Entonces** el sistema rechaza la solicitud e informa cuánto debe esperar.

7. **Escenario**: Primer inicio de sesión.
   - **Dado** que la persona verifica un código y aún no existe un usuario con ese correo.
   - **Cuando** el acceso es correcto.
   - **Entonces** el sistema crea el usuario automáticamente y le pide su nombre, necesario para el comprobante de reserva, antes de continuar.

8. **Escenario**: Sesión expirada.
   - **Dado** que han pasado 24 horas desde que se emitió el token.
   - **Cuando** la persona intenta realizar una acción.
   - **Entonces** el sistema le pide iniciar sesión de nuevo con un código nuevo y, al terminar, la devuelve a la pantalla en la que estaba. No hay renovación automática.

9. **Escenario**: Cierre de sesión.
   - **Dado** que la persona tiene una sesión iniciada.
   - **Cuando** cierra sesión.
   - **Entonces** el token se elimina de ese dispositivo y las sesiones abiertas en otros dispositivos siguen vigentes hasta que expiren.

10. **Escenario**: Inicio de sesión al escanear el QR de un espacio sin sesión vigente.
    - **Dado** que la persona escanea el QR de un espacio sin token o con el token expirado.
    - **Cuando** el sistema detecta que no está autenticada.
    - **Entonces** el sistema le pide iniciar sesión con correo y código y, una vez autenticada, continúa con el check-in en el mismo espacio (ver SPEC 005). Si el enlace se abre en un navegador distinto al de su sesión habitual, debe iniciar sesión de nuevo.

11. **Escenario**: Cambios de estado aplicados de inmediato.
    - **Dado** que la persona tiene un token vigente y, mientras tanto, es suspendida o cambia su rol.
    - **Cuando** realiza su siguiente petición.
    - **Entonces** el sistema aplica su estado actual, sin esperar a que el token expire.

### Casos Extremos (Edge Cases)

- **¿Las reglas de acceso son distintas para estudiantes, docentes y funcionarios?**
  No. Los tres tienen las mismas reglas y la misma cuota (RN-02).

- **¿Puede iniciar sesión un Usuario suspendido?**
  Sí. Un Usuario suspendido puede consultar disponibilidad y sus reservas, pero no puede crear nuevas hasta que termine la suspensión (RN-05).

- **¿Cuál es el dominio institucional válido?**
  El contexto indica `@unimagdalena.edu.co` y señala que el dominio exacto debe confirmarse. El dominio es un parámetro configurable (ver SPEC 006), no un valor fijo en el código.

- **¿Cómo se obtiene el rol de Administrador?**
  No se obtiene por autoservicio: se asigna a correos institucionales específicos desde la configuración del sistema (RN-60). El Administrador inicia sesión con el mismo mecanismo que el Usuario.

- **¿Por qué el token no usa cookies?**
  Por decisión del contexto (RN-55): el cliente guarda el token en el almacenamiento del navegador y lo envía en el encabezado `Authorization: Bearer`. Como consecuencia, el token no se comparte entre navegadores (ver escenario 10).

- **¿Se puede renovar la sesión antes de que expire?**
  No. Pasadas las 24 horas se debe iniciar sesión de nuevo (RN-56).

- **¿Puede tener la misma persona sesiones en varios dispositivos?**
  Sí. Cada inicio de sesión genera un token independiente (RN-57).

- **¿Qué ocurre si el correo con el código no se puede enviar?**
  El sistema informa a la persona y le permite solicitar otro código. El comportamiento general ante fallos de correo está pendiente de definir (ver SPEC 011).

- **¿Se puede cerrar la sesión de un dispositivo perdido?**
  **[Pendiente de definir]** El contexto no define revocación de tokens; un token vigente sigue siendo válido hasta que expire (máximo 24 horas) o hasta que el usuario sea suspendido o cambie su estado (RN-58).

- **¿El método de acceso es definitivo?**
  No necesariamente. El código de acceso es sustituible por el inicio de sesión con Microsoft de la universidad sin cambiar los demás casos de uso (ver plan técnico, D-08 y D-09).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir iniciar sesión con el correo institucional y un código de acceso de un solo uso enviado a ese correo, sin contraseñas ni registro previo (RF-01, RN-49).
- **RF-002**: El sistema DEBE validar el dominio al solicitar el código, rechazar los correos que no pertenezcan al dominio institucional configurado y no enviar ningún correo en ese caso (RN-01, RN-50).
- **RF-003**: El sistema DEBE generar el código con 6 dígitos y un generador criptográficamente seguro, con vigencia de 10 minutos y un solo uso; solicitar un código nuevo DEBE invalidar el anterior, y el sistema DEBE guardar únicamente su hash (RN-51).
- **RF-004**: El sistema DEBE admitir un máximo de 5 intentos fallidos por código y, al superarlos, invalidarlo (RN-52).
- **RF-005**: El sistema DEBE exigir al menos 60 segundos entre dos solicitudes de código para el mismo correo, permitir como máximo 5 solicitudes por hora por correo e informar cuánto debe esperar la persona (RN-53).
- **RF-006**: El sistema DEBE emitir, al verificar un código correcto, un token JWT firmado con vigencia de 24 horas desde su emisión, que incluya como mínimo el identificador del usuario, su correo, su rol, la fecha de emisión y la de expiración (RN-54).
- **RF-007**: El sistema NO DEBE usar cookies para la sesión: el token se envía en cada petición en el encabezado `Authorization: Bearer` (RN-55).
- **RF-008**: El sistema NO DEBE renovar el token automáticamente; al expirar, DEBE pedir iniciar sesión de nuevo y devolver a la persona a la pantalla en la que estaba (RN-56).
- **RF-009**: El sistema DEBE permitir cerrar sesión eliminando el token del dispositivo, y permitir sesiones abiertas en varios dispositivos a la vez (RN-57).
- **RF-010**: El sistema DEBE consultar en cada petición el estado vigente del usuario (existencia, rol y suspensión) y NO DEBE deducirlo del token (RN-58).
- **RF-011**: El sistema DEBE crear el usuario automáticamente al completar su primer inicio de sesión y pedirle su nombre (RN-59).
- **RF-012**: El sistema DEBE asignar el rol de Administrador solo a correos institucionales específicos definidos en la configuración (RN-60).
- **RF-013**: El sistema DEBE aplicar las mismas reglas de acceso a estudiantes, docentes y funcionarios (RN-02).
- **RF-014**: El sistema DEBE permitir el inicio de sesión de un Usuario suspendido, limitando sus acciones a consultar disponibilidad y sus reservas (RN-05).
- **RF-015**: El sistema DEBE, ante un escaneo de QR sin sesión vigente, pedir el inicio de sesión y continuar con el check-in una vez autenticada la persona; si el enlace se abre en un navegador distinto, DEBE pedir iniciar sesión de nuevo (RN-34).
- **RF-016**: El sistema DEBE entregar el código de acceso mediante el Servicio de correo al correo institucional de la persona (RF-20).
- **RF-017**: El sistema DEBE registrar en auditoría cada solicitud de código, cada inicio de sesión exitoso o fallido y cada cierre de sesión, sin incluir códigos ni tokens (RN-61, RNF-08).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE guardar únicamente los datos necesarios del usuario: nombre y correo institucional; no DEBE guardar contraseñas ni códigos de acceso en claro (RNF-09 del contexto).
- **RNF-002**: El flujo de inicio de sesión DEBE poder resolverse cómodamente desde el celular (diseño *mobile-first*, RNF-02 del contexto).
- **RNF-003**: Toda la comunicación DEBE ir por HTTPS; el token DEBE firmarse con una llave secreta fuera del código fuente, el servidor DEBE fijar el algoritmo de firma sin aceptarlo del cliente, y la aplicación DEBE aplicar medidas contra XSS (política de seguridad de contenido y escape de los datos mostrados), dado que el token vive en el navegador (RNF-10 del contexto).
- **RNF-004**: El correo con el código DEBE llegar en menos de 1 minuto en condiciones normales e indicar su vigencia y que no debe compartirse (RNF-11 del contexto).
- **RNF-005**: Las vigencias y las fechas de la sesión DEBEN manejarse en zona horaria America/Bogota (RNF-07 del contexto).

### Entidades Clave

- **Usuario**: Persona con correo institucional que usa la plataforma. Atributos: nombre y correo institucional, además de su rol (Usuario o Administrador) y su estado de suspensión (ver SPEC 010). Su identidad proviene del token verificado en cada petición (RN-32, RN-58).
- **CódigoDeAcceso**: Código numérico de un solo uso enviado al correo institucional. Atributos: correo, hash del código, vencimiento, intentos fallidos y marca de uso. Solo se guarda su hash.
- **TokenDeSesión (JWT)**: Credencial firmada con vigencia de 24 horas que el cliente conserva en el navegador. No se guarda en el servidor ni en cookies.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Control de Acceso, "100% de las solicitudes de código con correos fuera del dominio institucional son rechazadas y no generan ningún correo".
- **CE-002**: Consistencia de Reglas, "0 diferencias en las reglas de acceso entre estudiantes, docentes y funcionarios".
- **CE-003**: Código de Un Solo Uso, "0 códigos aceptados más de una vez, después de vencer o después de 5 intentos fallidos".
- **CE-004**: Vigencia de la Sesión, "0 tokens aceptados pasadas 24 horas desde su emisión; 100% de las expiraciones llevan a iniciar sesión y devuelven a la pantalla de origen".
- **CE-005**: Estado Vigente, "100% de las suspensiones y cambios de rol se aplican en la siguiente petición, sin esperar a que expire el token".
- **CE-006**: Protección de Credenciales, "0 códigos de acceso o tokens en claro almacenados en la base de datos o escritos en registros".
- **CE-007**: Continuidad del Check-in, "100% de los escaneos de QR sin sesión continúan con el check-in en el mismo espacio tras iniciar sesión en el mismo navegador".