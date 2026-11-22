# POST /admin/spaces

- **UC / SPEC**: UC07 — [Gestionar espacios y franjas](../../../features/007-gestionar-espacios-y-franjas/spec.md)
- **Rol**: Administrador
- **Autenticación**: `Authorization: Bearer <token>`

## Solicitud

### Cuerpo

La solicitud es `multipart/form-data` con tres partes obligatorias: `space` (JSON con `Content-Type: application/json`), `preview` y `panorama` (archivos de imagen). Cada archivo tiene un máximo de 30 MiB (31.457.280 bytes). El contenido debe ser reconocido por un lector ImageIO disponible; el tipo declarado por el cliente no determina el formato almacenado.

La solicitud completa admite hasta 64 MiB. El límite de ancho × alto es configurable mediante `APP_STORAGE_MAX_IMAGE_PIXELS` (64.000.000 por defecto) y se comprueba antes de decodificar. El backend sube los archivos originales a Supabase Storage privado y guarda solo sus referencias y metadatos en PostgreSQL; no hay carga directa del cliente a Supabase ni compresión.

Cada dimensión admite hasta `APP_STORAGE_MAX_IMAGE_DIMENSION` (16.384 por defecto). La validación decodifica una muestra acotada, que se descarta; no cambia el archivo original.

Campos de la parte `space`:

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `name` | string | Sí | Nombre del espacio. |
| `capacity` | integer | Sí | Aforo máximo. |
| `requirements` | string | No | Requisitos de uso. |
| `openingTime` | `HH:mm` | Sí | Inicio del horario de operación. |
| `closingTime` | `HH:mm` | Sí | Fin del horario de operación. |
| `status` | enum `SpaceStatus` | No | Por defecto `ACTIVE`. |
| `disciplines` | array `Discipline` | Sí | Al menos una disciplina; no puede contener simultáneamente `FOOTBALL_5` y `FOOTBALL_11`. |

```json
{
  "name": "Cancha de voleibol 1",
  "capacity": 12,
  "requirements": "Calzado deportivo",
  "openingTime": "06:00",
  "closingTime": "22:00",
  "status": "ACTIVE",
  "disciplines": ["VOLLEYBALL"]
}
```

## Respuestas

### 201 Created

```json
{
  "id": "3b2a1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "name": "Cancha de voleibol 1",
  "capacity": 12,
  "requirements": "Calzado deportivo",
  "openingTime": "06:00",
  "closingTime": "22:00",
  "status": "ACTIVE",
  "disciplines": ["VOLLEYBALL"]
}
```

### 400 Bad Request

```json
{
  "code": "VALIDATION_ERROR",
  "message": "El horario de cierre debe ser posterior al de apertura.",
  "details": {
    "fields": {
      "closingTime": "debe ser mayor que openingTime"
    }
  }
}
```

## Errores del dominio

| `code` | HTTP | Cuándo ocurre | Regla |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Campos o imágenes faltantes, imágenes inválidas o demasiado grandes, disciplinas vacías, combinación Fútbol 5 + Fútbol 11 u horario inválido. | RN-06, RN-08 |
| `TOKEN_EXPIRED` | 401 | El token venció. | RN-56 |
| `FORBIDDEN` | 403 | El usuario no tiene rol `ADMIN`. | D-21 |
| `STORAGE_NOT_CONFIGURED` | 503 | Falta el origen o la credencial de servidor de Storage. | Técnico |
| `STORAGE_UNAVAILABLE` | 503 | Fallo de red, permisos o respuesta no exitosa de Storage. | Técnico |

## Notas

- Las franjas de 60 minutos se derivan del horario de operación (RN-08).
- Un espacio nuevo puede quedar en `ACTIVE` o directamente en `MAINTENANCE`.
- La incompatibilidad de Fútbol 5 + Fútbol 11 devuelve `details.fields.disciplines`; las demás combinaciones siguen permitidas.
- No se confirman referencias parciales. Los objetos de una operación fallida quedan en una cola persistente para limpieza tras una hora de margen; no implica un borrado inmediato.
