# Orden de implementación de los casos de uso

Orden recomendado según las dependencias de `docs/techincal-plan/plan-general.md`, §11. UC02, UC07 y UC11 se implementan por etapas para evitar dependencias circulares.

| Paso | UC / etapa | Qué completar |
|---|---|---|
| 0 | **Base técnica** | Verificar PostgreSQL, esquema compartido, configuración, correo base, parámetros y auditoría. Reutilizar lo implementado. |
| 1 | **UC01 — Iniciar sesión** + **UC11-H3** | Completar autenticación, JWT, perfil, reautenticación y envío del código por correo. |
| 2 | **UC06 — Reglas de uso** | Completar consulta y configuración de las reglas que consumirán los demás UC. |
| 3 | **UC07-H1 — Gestionar espacios** | Completar creación, edición, horarios, disciplinas, aforo y mantenimiento. |
| 4 | **UC05-H2 — QR del espacio** | Implementar el QR necesario para el posterior registro de asistencia. |
| 5 | **UC02 — Disponibilidad base** | Implementar listado de espacios, franjas, tolerancia, horizonte, seguridad y canal SSE. Preparar las consultas de ocupación y bloqueos; el cierre de eventos espera las operaciones propietarias. |
| 6 | **UC03 — Reservar espacio** + **UC11-H1** | Completar reservas y comprobantes. Integrar la ocupación y los eventos de nuevas reservas con UC02. |
| 7 | **UC08 — Consultar cuota semanal** | Completar el cálculo y consulta de cuota a partir de las reservas. |
| 8 | **UC04 — Gestionar mis reservas** | Implementar primero listado y después cancelación. Integrar eventos de disponibilidad y el correo de cancelación de UC11. |
| 9 | **UC05-H1 — Confirmar asistencia** | Completar el check-in usando reservas y QR del espacio. |
| 10 | **UC07-H2 — Bloqueos institucionales** | Completar bloqueos, recurrencias y cancelaciones asociadas. Integrar avisos por correo y eventos de disponibilidad. |
| 11 | **UC02 — Cierre integral** | Verificar todos los productores SSE: reserva, cancelación, bloqueo y cambio de espacio. Completar aceptación, contratos, integración y rendimiento; cerrar T001–T015 con evidencia. |
| 12 | **UC10 — Aplicar sanciones** | Completar procesamiento de inasistencias y sanciones, con sus notificaciones. |
| 13 | **UC11 — Cierre de correos** | Completar recordatorios y verificar todos los envíos integrados en los pasos anteriores. |
| 14 | **UC12 — Contactar administrador** | Completar el flujo de contacto. |
| 15 | **UC09 — Panel de administración** | Completar consultas e indicadores con los flujos anteriores disponibles. |
| 16 | **UC13 — Solicitar implementos extras** | Completar gestión, disponibilidad y solicitud de implementos; integrar su información en el comprobante. |
| 17 | **Verificación final** | Ejecutar las verificaciones de cada plan y `./mvnw clean verify`; comprobar las integraciones afectadas por UC13. |
