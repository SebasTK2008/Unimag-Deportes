# GET /health, /health/liveness y /health/readiness

- **Uso**: sondas operativas de Actuator.
- **Autenticación**: públicas; las sondas se envían sin `Authorization`.

## Respuestas

- `200 OK`: estado `UP`.
- `503 Service Unavailable`: estado no saludable (`DOWN` u `OUT_OF_SERVICE`).

El cuerpo usa el formato de Actuator, no `ApiError`:

```json
{"status":"UP"}
```

La respuesta agregada de `/health` puede incluir los nombres de los grupos. No se exponen detalles, componentes, credenciales ni direcciones internas.

## Alcance

- `/health/liveness`: estado de vida de la aplicación, sin consultar servicios externos.
- `/health/readiness`: estado de aceptación de tráfico y disponibilidad de PostgreSQL.
- `/health`: estado agregado de los indicadores habilitados.

SMTP está deshabilitado como indicador y Supabase Storage no tiene indicador registrado. Un `200` no prueba entrega de correo, existencia del bucket, permisos ni cuotas de Storage. La imagen y Compose comprueban readiness mediante HTTP, no solo apertura del puerto.

Solo estas tres rutas son públicas. No se habilitan otros endpoints de Actuator ni acceso anónimo a detalles por componente.
