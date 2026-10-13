# Unimag Deportes — Reglas de negocio y funcionalidades


---

## 1. Alcance

Unimag Deportes centraliza la reserva de canchas de la universidad para cinco disciplinas: **baloncesto, voleibol, fútbol 5, fútbol 11 y tenis de campo**. Reemplaza el proceso actual con formularios de Microsoft Forms por una aplicación web con disponibilidad en tiempo real, confirmación inmediata y reglas de uso aplicadas automáticamente, sin intervención del administrador en cada reserva.

**Principios del sistema**

1. **No hay cobros monetarios.** El único mecanismo de control es el **límite de uso semanal** por persona. **[Definido]**
2. **La confirmación es automática** si el espacio está libre y el usuario cumple las reglas.
3. **Cero cruces:** un espacio nunca puede tener dos reservas activas en la misma franja.
4. **Las reglas se aplican igual para todos** los usuarios.

**Fuera del alcance:** pagos, alquiler a personas externas a la universidad, inscripción a torneos, reserva de implementos deportivos.

---

## 2. Actores

| Actor | Descripción |
|---|---|
| **Usuario** | Estudiante, docente o funcionario con correo institucional. Reserva, cancela y confirma asistencia. |
| **Administrador** | Personal del área de deportes. Configura espacios, reglas y bloqueos, y consulta el panel de ocupación. |
| **Sistema** | Ejecuta automáticamente las validaciones, el bloqueo de franjas, las sanciones y las notificaciones. |
| **Servicio de correo** | Actor externo que entrega comprobantes, recordatorios y avisos al correo institucional. |

---

## 3. Glosario

| Término | Significado |
|---|---|
| **Espacio** | Cancha o área física reservable (por ejemplo, "Cancha de tenis 1"). |
| **Franja** | Bloque de tiempo reservable de un espacio (por ejemplo, 2:00 p. m. a 3:00 p. m.). |
| **Reserva** | Asignación de una franja de un espacio a un usuario. |
| **Cuota semanal** | Máximo de horas de uso que un usuario puede acumular en una semana. |
| **Tolerancia** | Minutos después del inicio de una franja en los que aún se puede reservar un espacio libre. |
| **Check-in** | Confirmación de asistencia escaneando el QR del espacio al ingresar. |
| **Cancelación tardía** | Cancelación realizada con menos de 1 hora de anticipación. |
| **Inasistencia** | Reserva en la que el usuario no hizo check-in dentro del plazo. |
| **Bloqueo institucional** | Franja retirada de la reserva libre por clases, eventos o mantenimiento. |

---

## 4. Parámetros configurables

Estos valores **no deben quedar fijos en el código**: el administrador debe poder cambiarlos desde la configuración.

| Parámetro | Valor | 
|---|---|---|
| Cobros monetarios | Ninguno | 
| Tolerancia para reservar una vez iniciada la franja | **10 minutos** | 
| Umbral de cancelación tardía | **Menos de 60 minutos** antes del inicio | 
| Duración de una franja | 60 minutos | 
| Duración máxima de una reserva | 2 franjas consecutivas (2 horas) | 
| Cuota semanal por usuario | 4 horas por semana | 
| Inicio de la semana de conteo | Lunes 00:00, zona horaria America/Bogota | 
| Anticipación mínima para reservar | 0 minutos (se permite hasta el fin de la tolerancia) | 
| Anticipación máxima para reservar | 7 días | 
| Ventana de check-in | Desde 10 min antes del inicio hasta el fin de la tolerancia | 
| Plazo de check-in en reservas hechas dentro de la tolerancia | 10 minutos desde que se crea la reserva | 
| Recordatorio previo a la reserva | 2 horas antes del inicio | 
| Suspensión por inasistencias | 3 inasistencias en 30 días suspenden 7 días |
| Dominio de correo permitido | `@unimagdalena.edu.co` (confirmar dominio exacto) | 

---

## 5. Reglas de negocio

### 5.1 Identidad y acceso

| ID | Regla |
|---|---|
| RN-01 | Solo pueden usar la plataforma personas con correo del dominio institucional. El sistema valida el dominio al iniciar sesión. |
| RN-02 | Estudiantes, docentes y funcionarios tienen las mismas reglas y la misma cuota.  |
| RN-03 | Las reservas son **nominales**: quedan a nombre del usuario que las crea y no se pueden transferir. |
| RN-04 | Quien crea la reserva es quien debe asistir y hacer el check-in. |
| RN-05 | Un usuario suspendido puede consultar disponibilidad y sus reservas, pero no puede crear nuevas hasta que termine la suspensión. |

### 5.2 Espacios y franjas

| ID | Regla |
|---|---|
| RN-06 | Cada espacio tiene: nombre, disciplina(s), aforo máximo, requisitos de uso, horario de operación y estado (activo o en mantenimiento). |
| RN-07 | Si un mismo espacio físico sirve para varias disciplinas (por ejemplo, baloncesto y voleibol), **la reserva bloquea el espacio completo**, sin importar la disciplina elegida. |
| RN-08 | Las franjas son de 60 minutos dentro del horario de operación de cada espacio. |
| RN-09 | Un espacio en mantenimiento no aparece como reservable. |

### 5.3 Reservas

| ID | Regla |
|---|---|
| RN-10 | **Cero cruces:** un espacio solo puede tener una reserva activa por franja. La verificación y el bloqueo ocurren en una sola operación transaccional; si dos usuarios intentan reservar a la vez, gana el primero y el segundo recibe el aviso "La franja ya no está disponible" con el calendario actualizado. |
| RN-11 | Un usuario no puede tener dos reservas que se solapen en el tiempo, aunque sean en espacios distintos. |
| RN-12 | La reserva solo se confirma si el correo es válido, el usuario no está suspendido, la franja está libre y el usuario tiene cuota suficiente. |
| RN-13 | Si todas las validaciones pasan, la reserva se confirma al instante en pantalla y se envía el comprobante al correo. No hay aprobación manual. |
| RN-14 | Solo se puede reservar con hasta 7 días de anticipación.  |
| RN-15 | Una reserva puede abarcar hasta 2 franjas consecutivas del mismo espacio.  |

### 5.4 Cuota semanal

| ID | Regla |
|---|---|
| RN-16 | Cada usuario tiene una cuota máxima de horas por semana (3 h por defecto). La cuota se mide en horas reservadas, no solo en horas efectivamente usadas, para evitar acaparar franjas. |
| RN-17 | La semana de una reserva es la de la **fecha de la franja**, no la fecha en que se creó la reserva. |
| RN-18 | Antes de confirmar, el sistema suma las horas que ya cuentan en esa semana más las de la nueva reserva. Si superan la cuota, rechaza la reserva e informa cuántas horas le quedan al usuario. |
| RN-19 | El usuario puede ver en todo momento sus horas usadas y disponibles de la semana. |

**Qué cuenta contra la cuota según el estado de la reserva**

| Estado de la reserva | ¿Cuenta contra la cuota? |
|---|---|
| Confirmada (aún no ocurre) | Sí |
| En uso / Completada | Sí |
| Cancelada a tiempo | **No** (las horas se devuelven) |
| Cancelada tardía | **Sí** |
| Inasistencia | **Sí** |
| Cancelada por el administrador | **No** |

### 5.5 Cancelación

| ID | Regla |
|---|---|
| RN-20 | El usuario puede cancelar su reserva **en cualquier momento** desde "Mis reservas", sin autorización del administrador. |
| RN-21 | **Cancelación a tiempo** (faltan 60 minutos o más para el inicio): el espacio queda libre y las horas se devuelven a la cuota. |
| RN-22 | **Cancelación tardía** (faltan menos de 60 minutos, o la franja ya empezó sin check-in): el espacio queda disponible para otros usuarios, pero **las horas siguen contando como usadas**. |
| RN-23 | El objetivo de RN-22 es impedir que alguien cancele a último momento y luego use la cancha sin descontar horas. |
| RN-24 | Una reserva con check-in ya registrado no se puede cancelar. |
| RN-25 | El sistema muestra al usuario, antes de confirmar la cancelación, si esta será tardía y qué efecto tendrá sobre su cuota. |
| RN-26 | Cada cancelación genera una notificación por correo. |

### 5.6 Tolerancia

| ID | Regla |
|---|---|
| RN-27 | Si un espacio está libre en una franja que ya comenzó, aún se puede reservar hasta **10 minutos después del inicio**. |
| RN-28 | Pasada la tolerancia, la franja deja de ser reservable aunque siga libre. |
| RN-29 | Una reserva hecha dentro de la tolerancia ocupa la franja completa y cuenta la franja completa contra la cuota. |
| RN-30 | Una reserva hecha dentro de la tolerancia debe confirmarse con el QR dentro de los 10 minutos siguientes a su creación.  |

*Ejemplo: en la franja de 2:00 a 3:00 p. m. un espacio libre se puede reservar hasta las 2:10 p. m.

### 5.7 Check-in con código QR

| ID | Regla |
|---|---|
| RN-31 | Cada espacio tiene un **código QR estático** impreso en su entrada. El usuario lo escanea con su celular al ingresar. No interviene ningún vigilante ni el administrador.|
| RN-32 | El QR contiene un enlace fijo al espacio (por ejemplo, `/checkin/{id_espacio}`). No identifica al usuario: la identidad sale de su sesión iniciada. |
| RN-33 | Al escanear, el sistema comprueba que el usuario esté autenticado, que tenga una reserva activa en ese espacio y que el momento esté dentro de la ventana de check-in. Si todo coincide, la reserva pasa a "En uso". |
| RN-34 | Si no hay sesión iniciada, el sistema pide iniciarla y continúa con el check-in. |
| RN-35 | Si el check-in falla, se muestra el motivo: no tiene reserva en este espacio, es demasiado temprano, la ventana ya cerró o la reserva fue cancelada. |
| RN-36 | La ventana de check-in va desde 10 minutos antes del inicio hasta el fin de la tolerancia.  |

### 5.8 Inasistencias y sanciones

No hay multas. La sanción es **perder las horas** y, de repetirse, una **suspensión temporal**.

| ID | Regla |
|---|---|
| RN-37 | Si no hay check-in al cerrar la ventana, la reserva pasa a "Inasistencia" y sus horas cuentan contra la cuota (RN-16). |
| RN-38 | Tres inasistencias en 30 días suspenden automáticamente al usuario por 7 días.  |
| RN-39 | El usuario recibe un correo cuando se registra una inasistencia y cuando es suspendido, con la fecha en que podrá reservar de nuevo. |
| RN-40 | Las reservas futuras de un usuario suspendido se mantienen, pero no puede crear nuevas.  |

### 5.9 Bloqueos institucionales

| ID | Regla |
|---|---|
| RN-41 | El administrador puede bloquear franjas por **clases y grupos formativos** (bloqueos fijos y recurrentes), **eventos o torneos** (bloqueos puntuales) y **mantenimiento**. |
| RN-42 | Los bloqueos institucionales tienen prioridad sobre las reservas libres. |
| RN-43 | Si se crea un bloqueo sobre franjas con reservas existentes, esas reservas se cancelan automáticamente, el usuario recibe un aviso con el motivo, y **las horas no cuentan** contra su cuota. |
| RN-44 | Las franjas bloqueadas aparecen en el calendario como no disponibles, con el motivo visible. |

### 5.10 Notificaciones

| ID | Regla |
|---|---|
| RN-45 | **Comprobante:** al confirmar una reserva, el sistema envía por correo un comprobante en PDF con espacio, fecha, hora, nombre del usuario y reglas de cancelación. |
| RN-46 | **Recordatorio:** se envía 2 horas antes del inicio, con enlace para cancelar.  |
| RN-47 | Se notifica por correo cancelaciones, bloqueos que afecten una reserva, inasistencias y suspensiones. |
| RN-48 | Los correos incluyen un canal para contactar al administrador. |

---

## 6. Ciclo de vida de una reserva

```
                    ┌─────────────────────────┐
                    │       CONFIRMADA        │
                    └───┬─────────┬─────────┬─┘
        cancela ≥60 min │         │ cancela │ check-in OK
                        │         │  <60min │
                        ▼         ▼         ▼
             CANCELADA A    CANCELADA     EN USO ──► COMPLETADA
                TIEMPO       TARDÍA
                                 
   Sin check-in al cerrar la ventana ──► INASISTENCIA
   Bloqueo institucional ──► CANCELADA POR ADMINISTRADOR
```

| Estado | Descripción | ¿Libera el espacio? | ¿Cuenta cuota? |
|---|---|---|---|
| Confirmada | Reserva vigente a la espera de la franja. | No | Sí |
| En uso | El usuario hizo check-in. | No | Sí |
| Completada | La franja terminó con asistencia. | — | Sí |
| Cancelada a tiempo | Cancelada con 60 min o más de anticipación. | Sí | No |
| Cancelada tardía | Cancelada con menos de 60 min. | Sí | Sí |
| Inasistencia | No hubo check-in en la ventana. | — | Sí |
| Cancelada por administrador | Cancelada por un bloqueo institucional. | Sí | No |

---

## 7. Funcionalidades

### 7.1 Usuario

| ID | Funcionalidad | Descripción |
|---|---|---|
| RF-01 | Iniciar sesión | Acceso con correo institucional; rechaza otros dominios. |
| RF-02 | Consultar disponibilidad | Calendario interactivo en tiempo real por espacio y disciplina; muestra franjas libres, ocupadas y bloqueadas. |
| RF-03 | Reservar espacio deportivo | Elegir disciplina, espacio, fecha y franja, y confirmar. Valida correo, cuota, suspensión y disponibilidad; bloquea la franja de forma transaccional. |
| RF-04 | Recibir comprobante | Pantalla de confirmación y PDF enviado al correo. |
| RF-05 | Consultar mis reservas | Lista de reservas próximas y pasadas con su estado. |
| RF-06 | Cancelar reserva | Desde "Mis reservas"; advierte si la cancelación es tardía antes de confirmar. |
| RF-07 | Ver mi cuota semanal | Horas usadas, horas disponibles y fecha de reinicio. |
| RF-08 | Confirmar asistencia (QR) | Escanear el QR del espacio al ingresar. |
| RF-09 | Consultar reglas de uso | Cuota, tolerancia, cancelación, check-in y sanciones, en lenguaje claro. |
| RF-10 | Contactar administrador | Formulario que envía el mensaje al correo del área de deportes. |

### 7.2 Administrador

| ID | Funcionalidad | Descripción |
|---|---|---|
| RF-11 | Gestionar espacios y franjas | Crear, editar y desactivar espacios; definir disciplina, aforo, requisitos, horario de operación y estado. |
| RF-12 | Configurar reglas y cuotas | Editar los parámetros de la sección 4. |
| RF-13 | Bloquear franjas institucionales | Crear bloqueos puntuales o recurrentes con motivo; cancela las reservas afectadas. |
| RF-14 | Consultar panel de ocupación | Ocupación por espacio y franja, con filtros por fecha, disciplina y espacio. |
| RF-15 | Consultar indicadores de uso | Reservas, cancelaciones tardías, inasistencias y usuarios suspendidos. |
| RF-16 | Generar códigos QR | Generar e imprimir el QR de cada espacio. |

### 7.3 Sistema (automáticas)

| ID | Funcionalidad | Descripción |
|---|---|---|
| RF-17 | Validar reglas | Dominio, cuota, suspensión, solapes y disponibilidad en cada reserva. |
| RF-18 | Bloquear franja | Bloqueo transaccional que impide el doble agendamiento. |
| RF-19 | Aplicar sanciones | Marca inasistencias, descuenta horas y suspende según las reglas. |
| RF-20 | Enviar correos | Comprobante, recordatorio, cancelaciones, bloqueos, inasistencias y suspensiones. |
| RF-21 | Reiniciar cuota semanal | Cada semana la cuota vuelve al límite configurado. |

---

## 8. Requisitos no funcionales

| ID | Requisito |
|---|---|
| RNF-01 | Reservar en **5 pasos o menos**. |
| RNF-02 | Diseño **mobile-first**: todo flujo del usuario debe resolverse cómodamente desde el celular. |
| RNF-03 | Meta de usabilidad: completar una reserva en **40 segundos o menos** (KPI para las pruebas de usabilidad). |
| RNF-04 | Disponibilidad **en tiempo real**: el calendario refleja cambios al instante. |
| RNF-05 | Accesibilidad según **WCAG**. |
| RNF-06 | Identidad visual institucional (azul, dorado y rojo). |
| RNF-07 | Todas las fechas y horas en zona horaria America/Bogota. |
| RNF-08 | Registro de auditoría de reservas, cancelaciones, check-ins y sanciones. |
| RNF-09 | Solo se guardan los datos necesarios del usuario (nombre y correo institucional). |

---

## 9. Escenarios de ejemplo

**Escenario A: cuota semanal (límite de 4 h)**

| Acción | Horas que cuentan |
|---|---|
| Ana reserva 2 h el lunes | 2 de 3 |
| Ana reserva 1 h el miércoles | 3 de 3 |
| Ana intenta reservar 1 h más el jueves | **Rechazada**: sin cuota |
| Ana cancela la reserva del miércoles con 5 h de anticipación | 2 de 3 (devuelve 1 h) |
| Ana reserva 1 h el jueves | 3 de 3 |

**Escenario B: cancelación tardía**

Luis tiene reserva de 4:00 a 5:00 p. m. y cancela a las 3:30 p. m. El espacio queda libre para que otro usuario lo reserve, pero la hora de Luis **sigue contando** en su cuota. No puede usar la cancha sin reserva.

**Escenario C: tolerancia**

La cancha de tenis 2 está libre de 2:00 a 3:00 p. m. A las 2:08 p. m. Marta la reserva. Queda confirmada para la franja completa, debe escanear el QR antes de las 2:18 p. m., y se le descuenta 1 hora de cuota. A las 2:11 p. m. otro usuario ya no puede reservarla.

**Escenario D: inasistencia**

Pedro reserva 6:00 a 7:00 p. m. y no escanea el QR. Al cerrar la ventana de check-in, la reserva pasa a "Inasistencia", su hora sigue contando y recibe un correo. Si acumula 3 inasistencias en 30 días, queda suspendido 7 días.

**Escenario E: bloqueo por evento**

El administrador bloquea el fútbol 11 el sábado para un torneo. Las 4 reservas existentes ese día se cancelan automáticamente, los usuarios reciben un aviso con el motivo, y sus horas no cuentan.

---
