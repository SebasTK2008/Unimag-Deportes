# Especificación de Funcionalidad: UC16 - Generar códigos QR

**Creado**: 2026-10-13  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Generar e imprimir el QR de cada espacio (Prioridad: P1)

Como Administrador, quiero generar e imprimir el código QR de cada espacio, de manera que quede disponible en su entrada para que los Usuarios confirmen su asistencia.

**Por qué esta prioridad**: Sin el QR impreso en cada espacio no es posible el check-in (ver SPEC 007).

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

- **¿El QR identifica al Usuario?**
  No. Solo contiene el enlace fijo al espacio; la identidad del Usuario sale de su sesión iniciada (RN-32).

- **¿Cada espacio tiene su propio QR?**
  Sí. Cada espacio tiene un código QR estático impreso en su entrada (RN-31).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador generar el QR de cada espacio (RF-16).
- **RF-002**: El sistema DEBE generar el QR como un código estático que contiene un enlace fijo al espacio, sin identificar al Usuario (RN-31, RN-32).
- **RF-003**: El sistema DEBE permitir imprimir el QR (RF-16).

### Requisitos No Funcionales

- **RNF-001**: La generación DEBE cumplir WCAG (RNF-05 del contexto).

### Entidades Clave

- **Espacio**: Entidad definida en la SPEC 011. Su QR estático se deriva de su identificador (enlace fijo `/checkin/{id_espacio}`); no se modela como entidad independiente.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Correspondencia, "100% de los QR generados dirigen al enlace de check-in del espacio correcto".
- **CE-002**: Privacidad, "0 QR que contengan datos de un Usuario".
