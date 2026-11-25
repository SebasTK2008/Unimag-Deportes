# POST /admin/blocks/{id}/cancellation

- **UC / SPEC**: UC07, HU3; D-27.
- **Rol**: cualquier `ADMIN`, no solo quien creó el bloqueo.
- **Autenticación**: Bearer con perfil completo y reautenticación de los últimos diez minutos; no renueva `exp`.
- **Estado de entrega**: implementado; evidencia de pruebas en el plan UC07.

## Solicitud

`id` identifica una fila/franja de bloqueo, no toda la creación ni la serie recurrente.

```json
{"reason": "La clase fue cancelada"}
```

`reason` es obligatorio, de 1 a 1000 caracteres después de retirar espacios exteriores. Se conserva como `cancellationReason`, separado del motivo original.

## 200 OK

Devuelve el mismo objeto de bloqueo que contiene `items` en [GET /admin/blocks](GET-admin-blocks.md), con `status: CANCELLED`, fecha del servidor en America/Bogota, UUID del administrador y motivo de cancelación.

- Admite filas futuras o en curso: `now < endAt`. En el límite exacto del fin ya no se admite una primera cancelación.
- Conserva identidad, espacio, tipo, motivo original, inicio, fin y grupo recurrente. No borra ni edita esos datos.
- Solo cancela esa fila. No cancela filas hermanas, horas restantes ni toda la recurrencia.
- No reactiva ni modifica reservas anteriormente canceladas, cuotas, implementos ni el estado de mantenimiento del espacio.
- No envía correos de levantamiento. El espacio solo será reservable si cumple todas las demás reglas.

## Atomicidad e idempotencia

Adquiere `ReservationLock` antes de leer; relee estado y hora, persiste la transición y registra `BLOCK_CANCELLED` sobre `INSTITUTIONAL_BLOCK`, con actor, fecha y motivo en `detail`, en la misma transacción. Publica invalidación SSE después del commit. Un rollback no deja transición, auditoría ni evento de éxito.

Repetir con cuerpo válido devuelve `200` con la primera cancelación, incluso si la fila ya terminó o se presenta otro motivo/administrador. No sobrescribe actor, fecha o motivo ni añade auditoría de transición o evento SSE. La repetición sigue exigiendo autorización y reautenticación; un cuerpo inválido responde `400`.

## Errores

| Código | HTTP | Situación |
|---|---|---|
| `VALIDATION_ERROR` | 400 | ID, JSON o motivo inválido. |
| `TOKEN_INVALID` / `TOKEN_EXPIRED` | 401 | Bearer ausente, inválido o vencido. |
| `FORBIDDEN` / `PROFILE_INCOMPLETE` | 403 | Rol insuficiente o perfil incompleto. |
| `REAUTH_REQUIRED` | 403 | Reautenticación ausente o vencida. |
| `BLOCK_NOT_FOUND` | 404 | No existe el bloqueo. |
| `BLOCK_NOT_CANCELLABLE` | 409 | Primera cancelación de una fila finalizada. |
