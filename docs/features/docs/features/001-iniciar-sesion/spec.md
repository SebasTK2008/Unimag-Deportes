# Especificación de Funcionalidad: UC01 - Iniciar sesión

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Acceder a la plataforma con correo institucional (Prioridad: P1)

Como Usuario o Administrador, quiero iniciar sesión con mi correo institucional, de manera que el sistema valide que pertenezco a la universidad y me dé acceso a las funcionalidades de la plataforma.

**Por qué esta prioridad**: Es la puerta de entrada de la plataforma. Reservar, cancelar, confirmar asistencia y administrar la configuración requieren una identidad autenticada, y las reservas son nominales (RN-03).

**Prueba Independiente**: Intentar iniciar sesión con un correo del dominio institucional y con uno de otro dominio, y validar que el primero accede y el segundo es rechazado.

**Escenarios de Aceptación**:

1. **Escenario**: Inicio de sesión con correo institucional.
   - **Dado** que la persona tiene un correo del dominio institucional.
   - **Cuando** inicia sesión.
   - **Entonces** el sistema valida el dominio y le da acceso a la plataforma.

2. **Escenario**: Correo de otro dominio.
   - **Dado** que la persona intenta iniciar sesión con un correo que no pertenece al dominio institucional.
   - **Cuando** el sistema valida el dominio.
   - **Entonces** el sistema rechaza el acceso.

3. **Escenario**: Inicio de sesión al escanear el QR de un espacio sin sesión activa.
   - **Dado** que la persona escanea el QR de un espacio sin tener sesión iniciada.
   - **Cuando** el sistema detecta que no está autenticada.
   - **Entonces** el sistema le pide iniciar sesión y, una vez autenticada, continúa con el check-in (ver "Confirmar asistencia (QR)").

### Casos Extremos (Edge Cases)

- **¿Las reglas de acceso son distintas para estudiantes, docentes y funcionarios?**
  No. Los tres tienen las mismas reglas y la misma cuota (RN-02).

- **¿Puede iniciar sesión un Usuario suspendido?**
  Sí. Un Usuario suspendido puede consultar disponibilidad y sus reservas, pero no puede crear nuevas hasta que termine la suspensión (RN-05).

- **¿Cuál es el dominio institucional válido?**
  El contexto indica `@unimagdalena.edu.co` y señala que el dominio exacto debe confirmarse. El dominio es un parámetro configurable (ver "Configurar reglas de uso"), no un valor fijo en el código.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir iniciar sesión con correo institucional y validar el dominio al iniciar sesión (RF-01, RN-01).
- **RF-002**: El sistema DEBE rechazar el acceso de correos que no pertenezcan al dominio institucional configurado (RF-01).
- **RF-003**: El sistema DEBE aplicar las mismas reglas de acceso a estudiantes, docentes y funcionarios (RN-02).
- **RF-004**: El sistema DEBE permitir el inicio de sesión de un Usuario suspendido, limitando sus acciones a consultar disponibilidad y sus reservas (RN-05).
- **RF-005**: El sistema DEBE, ante un escaneo de QR sin sesión iniciada, pedir el inicio de sesión y continuar con el check-in una vez autenticada la persona (RN-34).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE guardar únicamente los datos necesarios del usuario: nombre y correo institucional (RNF-09 del contexto).
- **RNF-002**: El flujo de inicio de sesión DEBE poder resolverse cómodamente desde el celular (diseño *mobile-first*, RNF-02 del contexto).

### Entidades Clave

- **Usuario**: Persona con correo institucional que usa la plataforma. Atributos: nombre y correo institucional. Su identidad proviene de la sesión iniciada (RN-32).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Control de Acceso, "100% de los intentos de inicio de sesión con correos fuera del dominio institucional son rechazados".
- **CE-002**: Consistencia de Reglas, "0 diferencias en las reglas de acceso entre estudiantes, docentes y funcionarios".
