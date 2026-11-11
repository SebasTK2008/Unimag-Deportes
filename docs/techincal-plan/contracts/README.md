# Contratos REST — Unimag Deportes

Contratos de la API que consumen los clientes del backend (en particular el cliente web React, que vive en otro repositorio). Hay un archivo por endpoint; este documento fija las convenciones comunes.

- **Fuente funcional**: [`docs/reglas-y-contexto/unimagDeportes.md`](../../reglas-y-contexto/unimagDeportes.md)
- **Plan técnico**: [`plan-general.md`](../plan-general.md)
- **Especificaciones**: [`docs/features/`](../../features/)

## Convenciones

### Base y autenticación

- Las rutas son relativas a la raíz de la API.
- Salvo los endpoints públicos, se envía `Authorization: Bearer <token>`.
- El token se obtiene en `POST /auth/verify` y no viaja en cookies ni en la URL (RN-55).
- Las solicitudes con cuerpo usan `Content-Type: application/json`.

### Identificadores y fechas

- Identificadores: UUID en texto.
- Instantes: ISO-8601 con offset de America/Bogota, por ejemplo `2026-11-11T14:00:00-05:00`.
- Fechas sin hora: `YYYY-MM-DD`. Horas de operación: `HH:mm`.

### Enumeraciones

| Enum | Valores |
|---|---|
| `Role` | `USER`, `ADMIN` |
| `Discipline` | `BASKETBALL`, `VOLLEYBALL`, `FOOTBALL_5`, `FOOTBALL_11`, `TENNIS` |
| `SpaceStatus` | `ACTIVE`, `MAINTENANCE` |
| `ReservationStatus` | `CONFIRMED`, `IN_USE`, `COMPLETED`, `CANCELLED_ON_TIME`, `CANCELLED_LATE`, `NO_SHOW`, `CANCELLED_BY_ADMIN`, `CANCELLED_BY_SUSPENSION` |
| `SlotStatus` | `FREE`, `OCCUPIED`, `BLOCKED` |
| `EquipmentStatus` | `AVAILABLE`, `INACTIVE` |
| `BlockType` | `CLASS`, `EVENT`, `MAINTENANCE` |
| `Period` | `WEEKLY`, `MONTHLY` |

### Forma de error

Todos los errores usan el mismo cuerpo:

```json
{
  "code": "SLOT_TAKEN",
  "message": "La franja ya no está disponible.",
  "details": {}
}
```

- `code`: identificador estable para el cliente.
- `message`: texto en español para mostrar al usuario.
- `details`: objeto opcional (campos inválidos, horas restantes, unidades disponibles, etc.).

### Códigos HTTP

- `200` / `201` / `202`: éxito.
- `204`: éxito sin cuerpo.
- `400`: solicitud mal formada o valor inválido.
- `401`: token ausente, vencido o inválido.
- `403`: autenticado sin permiso, perfil incompleto, reautenticación requerida o usuario suspendido.
- `404`: recurso inexistente.
- `409`: conflicto con el estado actual (franja ocupada, cuota, solape, stock, cancelación no permitida).
- `429`: límite de solicitudes.
- `500`: error no controlado.

## Errores transversales

| `code` | HTTP | Cuándo ocurre |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Campos faltantes o inválidos; `details.fields` indica cuáles. |
| `TOKEN_EXPIRED` | 401 | Token vencido; el cliente debe iniciar sesión y volver a la pantalla de origen (RN-56). |
| `TOKEN_INVALID` | 401 | Firma, algoritmo o formato inválidos. |
| `PROFILE_INCOMPLETE` | 403 | Falta el nombre del primer ingreso; solo `/me`, `PUT /me/profile` y `/auth/*` están disponibles (RN-59). |
| `REAUTH_REQUIRED` | 403 | Acción sensible sin reautenticación de los últimos 10 minutos (D-21). |
| `FORBIDDEN` | 403 | Rol insuficiente o recurso de otro usuario. |
| `SUSPENDED` | 403 | Usuario suspendido intentando crear una reserva (RN-40). |
| `INTERNAL_ERROR` | 500 | Error no controlado. |

## Catálogo de errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `INVALID_EMAIL_DOMAIN` | 400 | El correo no pertenece al dominio institucional. | RN-50 |
| `INVALID_CODE` | 400 | El código de acceso no coincide. | RN-51 |
| `CODE_EXPIRED` | 400 | El código de acceso venció. | RN-51 |
| `CODE_INVALIDATED` | 400 | El código ya se usó o fue reemplazado por uno nuevo. | RN-51 |
| `TOO_MANY_ATTEMPTS` | 400 | El último intento fallido permitido invalida el código. | RN-52 |
| `RESEND_TOO_SOON` | 429 | Menos de 60 segundos desde la solicitud anterior. | RN-53 |
| `CODE_REQUEST_LIMIT` | 429 | Más de 5 solicitudes de código por hora para el correo. | RN-53 |
| `INVALID_DATE` | 400 | Fecha con formato inválido o inexistente. | — |
| `OUTSIDE_CALENDAR_HORIZON` | 400 | Fecha fuera de los 7 días del calendario. | OQ-09 |
| `INVALID_QUANTITY` | 400 | Cantidad de implemento menor o igual a cero. | RN-63 |
| `INVALID_PERIOD` | 400 | Período distinto de `WEEKLY` o `MONTHLY`. | OQ-08 |
| `SPACE_NOT_FOUND` | 404 | El espacio no existe. | — |
| `RESERVATION_NOT_FOUND` | 404 | La reserva no existe. | RN-03 |
| `EQUIPMENT_NOT_FOUND` | 404 | El implemento no existe. | RN-62 |
| `SLOT_TAKEN` | 409 | La franja ya tiene una reserva activa. | RN-10 |
| `BLOCKED_SLOT` | 409 | La franja tiene un bloqueo institucional. | RN-42 |
| `SPACE_IN_MAINTENANCE` | 409 | El espacio está en mantenimiento. | RN-09 |
| `OUTSIDE_BOOKING_WINDOW` | 409 | Fuera de la anticipación máxima o pasada la tolerancia. | RN-14, RN-27 |
| `OVERLAPPING_RESERVATION` | 409 | El usuario ya tiene una reserva que se solapa. | RN-11 |
| `QUOTA_EXCEEDED` | 409 | La reserva supera la cuota semanal disponible. | RN-18 |
| `EQUIPMENT_UNAVAILABLE` | 409 | Implemento inactivo o sin stock suficiente en la franja. | RN-62, RN-65 |
| `RESERVATION_NOT_CANCELLABLE` | 409 | La reserva ya tiene check-in o no se puede cancelar. | RN-24 |
| `NO_ACTIVE_RESERVATION` | 404 | No hay reserva del usuario en ese espacio para el check-in. | RN-33 |
| `CHECKIN_TOO_EARLY` | 409 | Aún no abrió la ventana aplicable a la reserva. | RN-36 |
| `CHECKIN_WINDOW_CLOSED` | 409 | La ventana de check-in ya cerró. | RN-36 |
| `RESERVATION_CANCELLED` | 409 | La reserva fue cancelada. | RN-35 |

## Índice de contratos

### Autenticación (UC01)

| Método | Ruta | Archivo |
|---|---|---|
| POST | `/auth/code` | [`auth/POST-auth-code.md`](./auth/POST-auth-code.md) |
| POST | `/auth/verify` | [`auth/POST-auth-verify.md`](./auth/POST-auth-verify.md) |
| GET | `/me` | [`auth/GET-me.md`](./auth/GET-me.md) |
| PUT | `/me/profile` | [`auth/PUT-me-profile.md`](./auth/PUT-me-profile.md) |
| POST | `/auth/logout` | [`auth/POST-auth-logout.md`](./auth/POST-auth-logout.md) |
| POST | `/auth/reauth/code` | [`auth/POST-auth-reauth-code.md`](./auth/POST-auth-reauth-code.md) |
| POST | `/auth/reauth/verify` | [`auth/POST-auth-reauth-verify.md`](./auth/POST-auth-reauth-verify.md) |

### Disponibilidad (UC02)

| Método | Ruta | Archivo |
|---|---|---|
| GET | `/spaces` | [`availability/GET-spaces.md`](./availability/GET-spaces.md) |
| GET | `/spaces/{id}/availability` | [`availability/GET-spaces-id-availability.md`](./availability/GET-spaces-id-availability.md) |
| GET | `/spaces/{id}/availability/stream` | [`availability/GET-spaces-id-availability-stream.md`](./availability/GET-spaces-id-availability-stream.md) |

### Reservas (UC03 y UC04)

| Método | Ruta | Archivo |
|---|---|---|
| POST | `/reservations` | [`reservations/POST-reservations.md`](./reservations/POST-reservations.md) |
| GET | `/reservations/mine` | [`reservations/GET-reservations-mine.md`](./reservations/GET-reservations-mine.md) |
| GET | `/reservations/{id}/cancellation-preview` | [`reservations/GET-reservations-id-cancellation-preview.md`](./reservations/GET-reservations-id-cancellation-preview.md) |
| POST | `/reservations/{id}/cancellation` | [`reservations/POST-reservations-id-cancellation.md`](./reservations/POST-reservations-id-cancellation.md) |

### Check-in y QR (UC05)

| Método | Ruta | Archivo |
|---|---|---|
| POST | `/checkin/{spaceId}` | [`checkin/POST-checkin-spaceId.md`](./checkin/POST-checkin-spaceId.md) |
| GET | `/admin/spaces/{id}/qr` | [`checkin/GET-admin-spaces-id-qr.md`](./checkin/GET-admin-spaces-id-qr.md) |

### Reglas y parámetros (UC06)

| Método | Ruta | Archivo |
|---|---|---|
| GET | `/rules` | [`rules/GET-rules.md`](./rules/GET-rules.md) |
| GET | `/admin/parameters` | [`rules/GET-admin-parameters.md`](./rules/GET-admin-parameters.md) |
| PUT | `/admin/parameters` | [`rules/PUT-admin-parameters.md`](./rules/PUT-admin-parameters.md) |

### Espacios y bloqueos (UC07)

| Método | Ruta | Archivo |
|---|---|---|
| GET | `/admin/spaces` | [`spaces/GET-admin-spaces.md`](./spaces/GET-admin-spaces.md) |
| POST | `/admin/spaces` | [`spaces/POST-admin-spaces.md`](./spaces/POST-admin-spaces.md) |
| PUT | `/admin/spaces/{id}` | [`spaces/PUT-admin-spaces-id.md`](./spaces/PUT-admin-spaces-id.md) |
| POST | `/admin/blocks` | [`blocks/POST-admin-blocks.md`](./blocks/POST-admin-blocks.md) |

### Cuota (UC08)

| Método | Ruta | Archivo |
|---|---|---|
| GET | `/quota/me` | [`quota/GET-quota-me.md`](./quota/GET-quota-me.md) |

### Panel de administración (UC09)

| Método | Ruta | Archivo |
|---|---|---|
| GET | `/admin/occupancy` | [`reports/GET-admin-occupancy.md`](./reports/GET-admin-occupancy.md) |
| GET | `/admin/indicators` | [`reports/GET-admin-indicators.md`](./reports/GET-admin-indicators.md) |

### Implementos (UC13)

| Método | Ruta | Archivo |
|---|---|---|
| GET | `/equipment` | [`equipment/GET-equipment.md`](./equipment/GET-equipment.md) |
| GET | `/equipment/availability` | [`equipment/GET-equipment-availability.md`](./equipment/GET-equipment-availability.md) |
| POST | `/admin/equipment` | [`equipment/POST-admin-equipment.md`](./equipment/POST-admin-equipment.md) |
| PUT | `/admin/equipment/{id}` | [`equipment/PUT-admin-equipment-id.md`](./equipment/PUT-admin-equipment-id.md) |

### Contacto (UC12)

| Método | Ruta | Archivo |
|---|---|---|
| POST | `/contact` | [`contact/POST-contact.md`](./contact/POST-contact.md) |

## Mantenimiento

- Al cambiar una ruta, un campo o un error, actualizar el archivo del endpoint y este índice.
- Los contratos deben reflejar lo que el código hace; si el código y el contrato difieren, se reporta antes de decidir cuál es correcto.
