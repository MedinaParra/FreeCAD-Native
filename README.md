# FreeCAD Android Native

Prototipo Android offline para visualizar geometrías 3D y avanzar hacia una integración nativa de FreeCAD.

## Rama principal preparada para Google AI Studio

La rama `main` está configurada para abrirse y ejecutarse en Google AI Studio sin NDK ni C++.

Características actuales:

- Kotlin y Jetpack Compose.
- Una sola actividad y un solo módulo `app`.
- Visor OpenGL ES 3.0.
- Rotación orbital y zoom.
- Backend geométrico local de demostración.
- Sin permiso de Internet.
- Sin Gemini API, Firebase, Retrofit, OkHttp, Room ni secretos.
- Sin configuración manual de keystore.

## Importar en Google AI Studio

1. Crea una nueva aplicación Android desde un repositorio de GitHub.
2. Selecciona `MedinaParra/FreeCAD-Native`.
3. Selecciona la rama `main`.
4. Mantén `freecad.native.enabled=false` en `gradle.properties`.
5. Ejecuta la vista previa.

Resultado esperado: la app abre en modo simulador y muestra una caja 3D que puede rotarse y ampliarse.

## Alcance real

Esta rama todavía no incorpora OpenCASCADE, FreeCAD Core, CPython, STEP, IGES ni FCStd. El objetivo inmediato de `main` es mantener una base Android estable y editable en AI Studio. La migración JNI/C++ debe trabajarse en una rama o build separado con Android Studio y Android NDK.

## Estructura principal

```text
app/src/main/java/com/medinaparra/freecadandroid/
├── MainActivity.kt
├── backend/
├── model/
├── ui/
└── viewer/
```

## Estado

- Interfaz Android: funcional.
- Visor 3D: funcional.
- Backend FreeCAD real: pendiente.
- Macros Python reales: pendientes.
- Importación STEP/FCStd: pendiente.
