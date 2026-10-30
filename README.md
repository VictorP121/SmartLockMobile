# SmartLock Mobile

**SmartLock Mobile** es una aplicación móvil desarrollada en ordenador, con las siguientes herramientas:
**Android Studio**
**Kotlin**
**Jetpack Compose**

unciona como interfaz de control de un sistema de seguridad IoT.
Permite abrir y cerrar una cerradura inteligente por Bluetooth,
con autenticación de usuarios, control de acceso por roles y registro de eventos.

## Qué puedes hacer con la app

- **Crear tu cuenta e iniciar sesión.** El primero que se registra queda como administrador.
- **Abrir y cerrar la cerradura con un PIN** (de 4 a 8 dígitos) y consultar si está abierta o cerrada.
- **Ver el historial:** quién entró, quién se equivocó con el PIN, cuándo se conectaron los celulares, etc.
- **Administrar** (solo el administrador): cambiar el PIN, borrar usuarios y limpiar el historial.
- **Protección contra intentos repetidos:** si te equivocas 3 veces seguidas, la app se bloquea por 30 segundos.

Las contraseñas y el PIN no se guardan en texto plano.

## Cómo probarla en tu computador (Windows con Android Studio)

Necesitas:
- [Android Studio](https://developer.android.com/studio), en una versión reciente.
- Un celular Android 7.0 o superior, con un cable USB.

### 1. Descarga el proyecto
- **Con ZIP:** en esta página, botón verde **Code** y luego **Download ZIP**. Después descomprímelo.
- **Con Git:** abre una terminal y escribe `git clone https://github.com/VictorP121/SmartLockMobile.git`

### 2. Ábrelo en Android Studio
1. Presiona **Open** (o **File > Open**) y elige la carpeta `SmartLockMobile`. Justo adentro tiene que estar el archivo `settings.gradle.kts`. Si ves otra carpeta con el mismo nombre, entra en ella.
2. Si te pregunta si confías en el proyecto, elige **Trust Project**.
3. Espera a que termine de cargar. La primera vez puede demorar varios minutos.

### 3. Activa el modo desarrollador en tu celular
1. Ve a **Ajustes > Acerca del teléfono** y toca 7 veces **Número de compilación**.
2. Vuelve a Ajustes, entra a **Opciones de desarrollador** y activa **Depuración USB**.

### 4.








# SmartLock Mobile


**SmartLock Mobile** es una aplicación móvil Android desarrollada con el software: **Android Studio**,
y mediante el lenguaje de programacion como **Kotlin** y **Jetpack Compose**
funcionan como interfaz de control de un sistema de seguridad IoT.
Permite abrir y cerrar una cerradura inteligente por Bluetooth, autenticación de usuarios,
control de acceso por roles y registro de eventos.

## Qué puedes hacer con la app

- **Crear tu cuenta e iniciar sesión.** El primero que se registra queda como administrador.
- **Abrir y cerrar la cerradura con un PIN** (de 4 a 8 dígitos) y consultar si está abierta o cerrada.
- **Ver el historial:** quién entró, quién se equivocó con el PIN, cuándo se conectaron los celulares, etc.
- **Administrar** (solo el administrador): cambiar el PIN, borrar usuarios y limpiar el historial.
- **Protección contra intentos repetidos:** si te equivocas 3 veces seguidas, la app se bloquea por 30 segundos.

Las contraseñas y el PIN no se guardan en texto plano, se alojan de manera encriptada para reforzar la integridad del usuario.

## Cómo probarla en tu computador (Windows con Android Studio)

Necesitas:
- [Android Studio](https://developer.android.com/studio), utiliza una versión reciente.
- Un celular Android 7.0 (Pixel 8) o superior, con un cable USB.

### 1. Descarga el proyecto
- **Con ZIP:** en esta página, botón verde **Code** y luego **Download ZIP**. Después descomprímelo.
- **Con Git:** abre una terminal y escribe `git clone https://github.com/VictorP121/SmartLockMobile.git`

### 2. Ábrelo en Android Studio
1. Presiona **Open** (o **File > Open**) y elige la carpeta `SmartLockMobile`. Justo adentro tiene que estar el archivo `settings.gradle.kts`. Si ves otra carpeta con el mismo nombre, entra en ella.
2. Si te pregunta si confías en el proyecto, elige **Trust Project**.
3. Espera a que termine de cargar. La primera vez puede demorar varios minutos.

### 3. Activa el modo desarrollador en tu celular
1. Ve a **Ajustes > Acerca del teléfono** y toca 7 veces **Número de compilación**.
2. Vuelve a Ajustes, entra a **Opciones de desarrollador** y activa **Depuración USB**.

### 4. Córrela
1. Conecta el celular al computador y, si te pregunta si confías en el equipo, acepta.
2. Selecciona tu celular en la barra de arriba de Android Studio.
3. Presiona el botón verde **Run**. En unos segundos la app se abre en tu celular.

## Cómo instalar el APK desde un dispositivo movil (sin Android Studio)

1. Descarga el APK directo desde [este link](https://github.com/VictorP121/SmartLockMobile/releases/download/v1.0/SmartLockMobile_V1.0.apk)
2. Ábrelo desde tu celular. permite orígenes desconocidos, permítelo solo para esta instalación.
3. Abre la app y acepta el permiso de Bluetooth cuando te lo pida.

## Hecha con
Kotlin, Jetpack Compose y SQLite.
