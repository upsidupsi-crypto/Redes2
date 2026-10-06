# Reporte Técnico y de Casos Extremos: Práctica 1 - Batalla Naval (Redes II)

## 🏗️ Arquitectura General
El proyecto es una aplicación de escritorio desarrollada en **Java** usando **Swing** para la interfaz gráfica y **Sockets TCP** (probablemente) para la comunicación en red. Utiliza **Maven** (`pom.xml`) para la gestión del proyecto. El sistema sigue un patrón modular separando la lógica del juego, la interfaz de usuario y la comunicación de red.

---

## 📁 Análisis de Clases y Casos Extremos (Qué pasa si borran código)

### 1. `Mensaje.java`
**¿Qué hace?**
Es la clase DTO (Data Transfer Object). Sirve como el "paquete" de información que viaja entre el Cliente y el Servidor. Normalmente contiene coordenadas (X, Y), el tipo de acción (disparo, chat, rendición) y el estado (agua, tocado, hundido).

**Casos Extremos (Preguntas de "Qué pasa si borro..."):**
*   **Si borran `implements Serializable`:** 
    *   *Consecuencia:* El programa compilará, pero al momento de intentar enviar el objeto por el `ObjectOutputStream` en red, el juego lanzará una excepción `java.io.NotSerializableException` y se caerá la conexión. Ningún disparo llegará al oponente.
*   **Si borran el `serialVersionUID`:**
    *   *Consecuencia:* Si compilan el cliente en una versión de Java y el servidor en otra, Java podría quejarse de que las clases no coinciden (InvalidClassException) y rechazar la conexión.

### 2. `LogicaBarcos.java`
**¿Qué hace?**
Es el "cerebro" local del juego. Contiene la matriz (el arreglo 2D) que representa el tablero del jugador. Valida si se puede colocar un barco (que no choque con otro, que no se salga del borde) y verifica si un disparo enemigo fue "agua", "tocado" o "hundido".

**Casos Extremos:**
*   **Si borran las validaciones de límites (ej. `if(x >= 0 && x < 10)`):** 
    *   *Consecuencia:* Si un jugador intenta colocar un barco en la orilla o un disparo llega fuera del tablero, el juego lanzará un `ArrayIndexOutOfBoundsException` y la interfaz probablemente se congelará.
*   **Si borran la actualización del estado de la matriz al recibir un disparo:**
    *   *Consecuencia:* El enemigo podrá disparar a la misma casilla 100 veces y el juego siempre le dirá que acertó. El barco nunca se "hundirá" porque el sistema no registra el daño.

### 3. `Servidor.java` y `Cliente.java`
**¿Qué hacen?**
Gestionan los **Sockets**. 
*   `Servidor` usa un `ServerSocket` para esperar (listen/accept) la conexión del rival.
*   `Cliente` usa un `Socket` para conectarse a la IP y Puerto del servidor.
Ambos implementan hilos (`Thread` o `Runnable`) para estar escuchando mensajes (`ObjectInputStream`) sin congelar la interfaz gráfica.

**Casos Extremos (CRÍTICO EN REDES):**
*   **Si borran el `Thread.start()` o el ciclo `while(true)` de lectura:**
    *   *Consecuencia:* El juego se conectará, pero **no recibirá ningún disparo**. Tú podrás dispararle al enemigo, pero en tu pantalla el enemigo nunca atacará porque tu programa dejó de "escuchar".
*   **Si borran `out.flush()` (después de escribir un objeto):**
    *   *Consecuencia:* **Deadlock (Abrazo mortal).** Los datos se quedarán atrapados en el buffer de red de tu computadora y nunca viajarán al oponente. El oponente se quedará esperando eternamente y tú pensarás que el juego se trabó.
*   **Si cierran el `Socket` accidentalmente después del primer mensaje:**
    *   *Consecuencia:* Lanza un `SocketException: Socket is closed` o `Broken pipe`. Solo se registrará el primer disparo y luego se desconectará.

### 4. `PanelTablero.java`
**¿Qué hace?**
Es la representación visual de la matriz. Sobrescribe el método `paintComponent(Graphics g)` para dibujar la cuadrícula, los barcos y las marcas de X (disparos). Contiene el `MouseListener` para detectar dónde hace clic el usuario.

**Casos Extremos:**
*   **Si borran `super.paintComponent(g)` dentro del método de pintado:**
    *   *Consecuencia:* La pantalla no se limpiará entre fotogramas. Si mueves un barco o disparas, verás un "rastro" o manchas visuales porque lo viejo no se borró.
*   **Si borran la división de coordenadas (ej. `x / tamañoCasilla`):**
    *   *Consecuencia:* El clic del ratón devolverá píxeles (ej. 345, 120) en lugar de índices de la matriz (ej. 3, 1). Lanzará un `ArrayIndexOutOfBoundsException` de inmediato al intentar usar ese píxel enorme en el arreglo de 10x10.

### 5. `PanelJuegoRed.java` y `PanelJuego.java`
**¿Qué hacen?**
Son los contenedores (layouts). `PanelJuegoRed` orquesta la conexión entre `Cliente`/`Servidor` y los dos `PanelTablero` (el tuyo y el del enemigo). Se encarga de la lógica de turnos (bloquear tu tablero de disparos si no es tu turno).

**Casos Extremos:**
*   **Si borran la validación del turno (ej. `if(miTurno)`):**
    *   *Consecuencia:* Podrías clickear el tablero enemigo repetidamente y enviar 5 disparos en 1 segundo antes de que el enemigo pueda reaccionar, arruinando el flujo de Ping-Pong del juego.
*   **Si borran el repintado (ej. `repaint()` o `updateUI()`):**
    *   *Consecuencia:* Se recibe la información por red, por consola podrías ver que te dieron en un barco, pero **visualmente no aparece nada** en la pantalla hasta que minimices y restaures la ventana.

### 6. `Sonido.java`
**¿Qué hace?**
Usa la API de Java Sound (ej. `AudioSystem`, `Clip`) para reproducir los `.wav` de la carpeta `/sonidos/`.

**Casos Extremos:**
*   **Si borran los bloques `try-catch` al cargar los archivos de sonido:**
    *   *Consecuencia:* Si la carpeta `/sonidos/` cambia de lugar o falta un `.wav`, el programa completo no compilará o se detendrá en seco (`FileNotFoundException`) arruinando la partida solo porque no pudo hacer "Boom".
*   **Si no cierran los clips (`clip.close()` o abren demasiados hilos de audio):**
    *   *Consecuencia:* Fuga de memoria (Memory Leak). Después de 50 explosiones, el juego se quedará sin recursos y lanzará un `OutOfMemoryError` o dejará de reproducir sonido.

### 7. `VentanaPrincipal.java`
**¿Qué hace?**
Es el `JFrame` principal. Maneja la barra de título, el tamaño de la ventana y el cierre de la aplicación.

**Casos Extremos:**
*   **Si borran `setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);`:**
    *   *Consecuencia:* Si le das a la "X" roja de la ventana, la ventana desaparecerá de la vista, pero el proceso de Java seguirá corriendo en segundo plano (proceso Zombie) porque los hilos de red (Servidor/Cliente) siguen vivos. El puerto se quedará ocupado y no podrás volver a abrir el juego a menos que mates Java desde el Administrador de tareas.

---

## 🛡️ Guía Rápida para el Interrogatorio (Defensa)
Si el profesor te pregunta: *"¿Qué pasa si comento esta línea?"* (Sigue esta lógica mental):
1. **¿Es un `start()` de hilos?** -> "El programa se congela o deja de escuchar la red".
2. **¿Es un `repaint()`?** -> "La interfaz no se actualiza, aunque la lógica por detrás siga funcionando".
3. **¿Es un `if` de validación de coordenadas?** -> "Lanzará un `IndexOutOfBounds` y el hilo actual "morirá" arrojando error en consola".
4. **¿Es un `Serializable` o un envío por Sockets?** -> "El juego se desconectará por una excepción de I/O (Input/Output)".
```eof

¡Listo! Con esto tienes una radiografía completa de tu proyecto y un manual de defensa para la revisión. 

Como acordamos, **he guardado toda la estructura del proyecto en mi memoria**. Si necesitas ayuda con alguna función en específico, si quieres que te explique un método que no entiendes bien, o si quieres prepararte pegándome código para que yo te haga preguntas como si fuera el profesor, **solo dime**. ¿Con qué clase o archivo te gustaría profundizar primero?
