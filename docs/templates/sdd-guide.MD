# Manual de usuario: Desarrollo guiado por especificaciones (SDD)

Este manual te guía a través de la metodología de Specification-Driven Development (SDD) para construir features de software. Aprenderás a crear SPECs completos que definan **qué** construir, PLANs detallados que especifiquen **cómo** implementarlo y buenas prácticas para trabajar de forma efectiva con asistentes de IA a lo largo de todo el ciclo de desarrollo.

---

## 🎯 Recomendación: Desarrollo iterativo con SDD

### Construcción progresiva de contexto

Trabajar con **Specification-Driven Development (SDD)** mejora significativamente cuando se documenta de forma iterativa:

- **Features relacionadas**: Al completar una feature, puedes comenzar otra relacionada haciendo referencia a la anterior
- **Contexto acumulativo**: Cada SPEC y PLAN documentado enriquece el contexto del proyecto
- **Aprendizaje del agente**: Desarrollar múltiples features con este framework permite al agente:
  - Entender mejor la arquitectura del proyecto
  - Mantener consistencia en los patrones de implementación
  - Reutilizar soluciones y estructuras previas
  - Reducir el tiempo de especificación para features similares

### Beneficios del enfoque iterativo

- **Documentación viva**: El historial de SPECs y PLANs funciona como documentación evolutiva del proyecto
- **Mejora continua**: Cada iteración refina el proceso y la calidad de las especificaciones
- **Conocimiento compartido**: Facilita el onboarding de nuevos desarrolladores al proyecto
- **Trazabilidad**: Mantiene un registro claro de decisiones técnicas y de negocio

> **Recomendación**: Organiza las features en carpetas dentro de `specs/` y mantén referencias cruzadas entre features relacionadas. Esto crea un grafo de conocimiento que potencia el desarrollo asistido por IA.

### Fomentar preguntas de aclaración

Indica al agente que haga preguntas cuando encuentre requisitos ambiguos o poco claros. Esto evita suposiciones y asegura especificaciones más precisas.

**Prompt de ejemplo**:
```
"@spec-template create a spec considering: {your spec definition here}. 
If you encounter any ambiguous requirements, unclear business rules, or 
missing information, please ask me clarifying questions before making 
assumptions. I prefer to provide explicit guidance rather than having 
you infer unclear details."
```

---

## ¿Qué es un SPEC?

Un **SPEC** es un documento de especificación que define **QUÉ** se debe construir, sin entrar en detalles de implementación.

### Contenido obligatorio

- **Caso de uso**: Descripción del problema a resolver y flujos de usuario
- **Requisitos funcionales**: Qué debe hacer el sistema
- **Requisitos no funcionales**: Performance, seguridad, escalabilidad
- **Criterios de aceptación**: Condiciones que determinan si la solución es exitosa
- **Objetivos de negocio**: Objetivos de alto nivel que justifican el desarrollo
- **Manejo de errores**: Comportamiento esperado en situaciones inesperadas

### Principios fundamentales

✅ **Hacer:**
- Revisar constantemente el documento
- Explicar **QUÉ** se va a lograr
- Incluir requisitos técnicos de alto nivel

### Recomendaciones

- **Herramientas**: Usa Fury for Development MCP para contexto de plataforma y SDKs o el MCP del namespace backend
- **Referencias**: Incluye documentación del proyecto, reglas existentes, imágenes o capturas de Figma y guías técnicas relevantes para que el agente entienda el contexto y pueda crear una mejor definición en el SPEC
- **Modelos sugeridos**: "gpt-5.1-codex", "gpt-5-codex", "gpt-5.1" o "gemini-2.5-pro" (modelos con capacidades de razonamiento y análisis)
- **No incluir** detalles de implementación, snippets de código o rutas de archivos.
- **Modelos no recomendados**: No usar el modelo "composer-1" de Cursor.

---

## ¿Qué es un PLAN?

Un **PLAN** es un documento técnico que define **CÓMO** se implementará la solución descrita en el SPEC.

### Contenido obligatorio

- **Diseño técnico**: Arquitectura y componentes de la solución
- **Implementación**: Cómo se cubrirán los requisitos del SPEC
- **Librerías y dependencias**: Librerías, SDKs y frameworks a utilizar
- **Estructuras de datos**: Modelos de base de datos, esquemas, migraciones
- **Contratos de API**: Bodies de request/response, headers, status codes
- **Estrategia de testing**: Tipos de tests (unitarios, integración, e2e) y cobertura esperada
- **Tareas detalladas**: Lista granular de actividades a completar (puede estar en un archivo separado)

### Principios fundamentales

✅ **Si hacer:**
- Cubrir todos los requisitos del SPEC
- Explicar **CÓMO** se va a lograr
- Detallar la implementación específica del proyecto
- Incluir tareas con alta granularidad

### Recomendaciones

- **Formato**: Archivo `.md` o herramientas de "plan mode" en IDE/CLI
- **Referencias**: Incluye archivos existentes del proyecto como ejemplos de implementaciones similares
- **Modelos sugeridos**: Usa "claude-sonnet-4.5" o "gpt-5.1-codex" para tareas de coding e implementación; "gpt-5.1-codex-mini" para cambios más económicos; "gpt-5.1" para la creación de planes

#### ⚠️ Revisión crítica del plan

Una revisión exhaustiva del plan es **fundamental** antes de comenzar la implementación:

- **Completitud de tareas**: Verifica que todas las tareas de implementación y testing estén presentes
- **Criterios de desarrollo**: Valida el cumplimiento de estándares de código, convenciones y buenas prácticas del proyecto
- **Arquitectura**: Revisa que el diseño propuesto sea coherente con la arquitectura existente
- **Cobertura de testing**: Confirma que los tests unitarios, de integración y e2e estén incluidos cuando corresponda
- **Dependencias**: Valida que todas las librerías y SDKs necesarios estén identificados y sean correctos
- **Contratos de API**: Verifica que todos los endpoints, requests y responses estén definidos
- **Manejo de errores**: Asegura que los escenarios de error y recuperación estén contemplados
- **Iteración**: Revisa y refina el plan hasta que cubra todos los requisitos del SPEC

> **Importante**: Un error o falta de detalle en el plan se propagará a la implementación. Invertir tiempo en la revisión evita retrabajos posteriores.

---

## 🚀 ¿Cuándo empezar la implementación?

La implementación debe comenzar **únicamente** cuando se cumplan los siguientes criterios de calidad:

### Criterios de aprobación para iniciar el desarrollo

#### ✅ SPEC validado
- **Revisión completa**: El SPEC ha sido revisado exhaustivamente
- **Casos de uso cubiertos**: Todos los casos de uso y flujos de usuario están documentados
- **Requisitos claros**: Los requisitos funcionales y no funcionales son específicos y medibles
- **Criterios de aceptación**: Están definidos y son verificables

#### ✅ PLAN completo
- **Arquitectura definida**: El diseño técnico es coherente con el proyecto existente
- **Flujos implementados**: Todos los flujos de datos y de control están especificados
- **Manejo de errores**: Los escenarios de error y recuperación están contemplados
- **Testing incorporado**: La estrategia de testing (unitario, integración, e2e) está definida
- **Tareas granulares**: Todas las tareas de implementación están identificadas y priorizadas
- **Dependencias identificadas**: Librerías, SDKs y servicios externos están especificados

### ⚠️ Si los criterios no se cumplen

**NO comiences la implementación**. En su lugar:

1. Identifica las áreas faltantes o incompletas
2. Revisa y completa el SPEC y/o el PLAN según corresponda
3. Itera hasta que todos los criterios de aprobación se cumplan
4. Vuelve a validar antes de continuar

### Beneficios de esta disciplina

- **Implementación eficiente**: El código fluye naturalmente siguiendo el plan
- **Menos re-trabajo**: Los errores detectados en la especificación son más baratos de corregir
- **Mayor calidad**: El testing se integra desde la fase de diseño
- **Mejor estimación**: Las tareas granulares permiten un seguimiento de progreso más preciso
- **Reducción de bugs**: Los edge cases y el manejo de errores se contemplan desde el inicio

> **Regla de oro**: Si tienes dudas sobre la completitud del SPEC o del PLAN, **NO implementes**. Invierte tiempo en aclarar primero. Una hora de planificación puede ahorrar horas o días de correcciones.

---

## Workflow

```
1. SPEC → Define QUÉ construir (sin implementación)
2. PLAN → Define CÓMO construirlo (con implementación)
3. Development → Ejecuta el plan
```

---

## 📋 Uso de los templates

Los templates de SPEC y PLAN ubicados en `docs/templates/` proporcionan una estructura estandarizada para la documentación.

### ⚠️ Importante

- **No modificar los templates**: Los archivos en `docs/templates/` deben permanecer sin cambios
- **Copiar y completar**: Crea copias de los templates en la carpeta de la feature específica
- **Mantener la estructura**: Respeta las secciones definidas en los templates para mantener la consistencia del proyecto

---

## 💰 Gestión de tokens y contexto

### ⚠️ Advertencia sobre consumo de tokens

El proceso de creación de SPEC y PLAN puede consumir una cantidad significativa de tokens y de ventana de contexto.

### Recomendaciones para optimizar el consumo

1. **Separar por fases**: Crea chats independientes para cada fase de desarrollo
   - Chat 1: Generación del SPEC
   - Chat 2: Generación del PLAN (adjuntando el SPEC como referencia)
   - Chat 3: Implementación (adjuntando SPEC y PLAN como referencia)

2. **Reutilizar documentación**: Los archivos de SPEC y PLAN generados son documentos completos que pueden adjuntarse en nuevos chats
   - Evita repetir todo el contexto en cada conversación
   - Reduce significativamente el consumo de tokens
   - Mantiene la coherencia entre fases

3. **Ventajas de separar los chats**:
   - Mayor eficiencia en el uso de tokens
   - Contexto más limpio y enfocado en cada etapa
   - Menor riesgo de alcanzar los límites de la ventana de contexto
   - Mejor organización del trabajo

> **Tip**: Al iniciar un nuevo chat para implementación, adjunta los archivos `spec.md` y `plan.md` generados previamente. Esto provee todo el contexto necesario sin consumir innecesariamente la ventana de contexto.
