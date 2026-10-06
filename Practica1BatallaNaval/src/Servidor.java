import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Servidor implements Runnable {

    private static final int PUERTO = 5000;
    private static final int TIEMPO_ESPERA_NOMBRE_MILISEGUNDOS = 10000;

    private static boolean hayPartidaEnCurso = false;

    private final Socket socketCliente;
    private final Random generadorAleatorio;
    private ObjectOutputStream flujoSalida;
    private ObjectInputStream flujoEntrada;
    private LogicaBarcos logicaServidor;
    private String nombreJugador;
    private int filaDisparoServidor;
    private int columnaDisparoServidor;

    public Servidor(Socket socketCliente) {
        this.socketCliente = socketCliente;
        this.generadorAleatorio = new Random();
    }

    public static void main(String[] argumentos) {
        ServerSocket socketServidor;
        try {
            socketServidor = new ServerSocket(PUERTO);
        } catch (IOException excepcion) {
            System.out.println("No se pudo abrir el puerto " + PUERTO + ": " + excepcion.getMessage());
            return;
        }

        System.out.println("Servidor de Batalla Naval esperando jugadores en el puerto " + PUERTO);

        while (true) {
            try {
                Socket socketNuevoCliente = socketServidor.accept();
                Thread hiloDelCliente = new Thread(new Servidor(socketNuevoCliente));
                hiloDelCliente.start();
            } catch (IOException excepcion) {
                System.out.println("Error al aceptar una conexion: " + excepcion.getMessage());
            }
        }
    }

    private static synchronized boolean reservarPartida() {
        if (hayPartidaEnCurso) {
            return false;
        }
        hayPartidaEnCurso = true;
        return true;
    }

    private static synchronized void liberarPartida() {
        hayPartidaEnCurso = false;
    }

    @Override
    public void run() {
        boolean partidaReservada = false;
        try {
            flujoSalida = new ObjectOutputStream(socketCliente.getOutputStream());
            flujoSalida.flush();
            flujoEntrada = new ObjectInputStream(socketCliente.getInputStream());
            socketCliente.setKeepAlive(true);

            socketCliente.setSoTimeout(TIEMPO_ESPERA_NOMBRE_MILISEGUNDOS);
            Mensaje mensajeNombre = recibirMensajeDeTipo(Mensaje.TIPO_NOMBRE);
            socketCliente.setSoTimeout(0);

            if (!reservarPartida()) {
                enviar(new Mensaje(Mensaje.TIPO_OCUPADO));
                System.out.println("Conexion rechazada: ya hay una partida en curso con otro jugador");
                return;
            }
            partidaReservada = true;

            nombreJugador = mensajeNombre.getTexto();
            System.out.println("Jugador conectado: " + nombreJugador);
            enviar(new Mensaje(Mensaje.TIPO_INICIO));

            recibirMensajeDeTipo(Mensaje.TIPO_LISTO);
            System.out.println(nombreJugador + " ya coloco su flota");

            logicaServidor = new LogicaBarcos();
            logicaServidor.colocarFlotaAleatoria();

            jugarPartida();
        } catch (IOException excepcion) {
            System.out.println("Se interrumpio la comunicacion con el cliente: " + excepcion.getMessage());
        } catch (ClassNotFoundException excepcion) {
            System.out.println("El cliente envio datos desconocidos: " + excepcion.getMessage());
        } finally {
            if (partidaReservada) {
                liberarPartida();
                System.out.println("Partida terminada. Esperando nuevos jugadores...");
            }
            try {
                socketCliente.close();
            } catch (IOException excepcion) {
                System.out.println("No se pudo cerrar la conexion: " + excepcion.getMessage());
            }
        }
    }

    private void enviar(Mensaje mensaje) throws IOException {
        flujoSalida.writeObject(mensaje);
        flujoSalida.flush();
        flujoSalida.reset();
    }

    private Mensaje recibirMensajeDeTipo(String tipoEsperado) throws IOException, ClassNotFoundException {
        Object objetoRecibido = flujoEntrada.readObject();
        if (!(objetoRecibido instanceof Mensaje)) {
            throw new IOException("Se recibio un objeto que no es un Mensaje");
        }
        Mensaje mensajeRecibido = (Mensaje) objetoRecibido;
        if (!tipoEsperado.equals(mensajeRecibido.getTipo())) {
            throw new IOException("Se esperaba un mensaje " + tipoEsperado + " y llego " + mensajeRecibido.getTipo());
        }
        return mensajeRecibido;
    }

    private void jugarPartida() throws IOException, ClassNotFoundException {
        boolean turnoDelCliente = generadorAleatorio.nextBoolean();

        Mensaje mensajeTurno = new Mensaje(Mensaje.TIPO_TURNO);
        if (turnoDelCliente) {
            mensajeTurno.setTexto(Mensaje.TURNO_CLIENTE);
            System.out.println("Empieza disparando " + nombreJugador);
        } else {
            mensajeTurno.setTexto(Mensaje.TURNO_SERVIDOR);
            System.out.println("Empieza disparando el servidor");
        }
        enviar(mensajeTurno);

        boolean partidaTerminada = false;
        while (!partidaTerminada) {
            if (turnoDelCliente) {
                partidaTerminada = atenderTurnoDelCliente();
            } else {
                partidaTerminada = jugarTurnoDelServidor();
            }
            mostrarTablerosEnConsola();
            turnoDelCliente = !turnoDelCliente;
        }
    }

    private boolean atenderTurnoDelCliente() throws IOException, ClassNotFoundException {
        int disparosValidos = 0;
        boolean clienteFalloElDisparo = false;
        boolean partidaTerminada = false;

        while (disparosValidos < LogicaBarcos.DISPAROS_POR_TURNO && !clienteFalloElDisparo && !partidaTerminada) {
            Mensaje mensajeDisparo = recibirMensajeDeTipo(Mensaje.TIPO_DISPARO);
            int fila = mensajeDisparo.getFila();
            int columna = mensajeDisparo.getColumna();
            String resultado = logicaServidor.recibirDisparo(fila, columna);

            Mensaje mensajeResultado = new Mensaje(Mensaje.TIPO_RESULTADO);
            mensajeResultado.setTexto(resultado);

            if (LogicaBarcos.RESULTADO_REPETIDO.equals(resultado) || LogicaBarcos.RESULTADO_INVALIDO.equals(resultado)) {
                enviar(mensajeResultado);
                continue;
            }

            boolean servidorPerdio = logicaServidor.todosLosBarcosHundidos();
            mensajeResultado.setFinDeJuego(servidorPerdio);
            if (LogicaBarcos.RESULTADO_HUNDIDO.equals(resultado)) {
                mensajeResultado.agregarBarcoHundido(logicaServidor.getBarcoHundidoEnUltimoDisparo());
            }
            enviar(mensajeResultado);

            System.out.println(nombreJugador + " disparo a " + LogicaBarcos.nombreDeCasilla(fila, columna) + ": " + resultado);
            disparosValidos++;

            if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
                clienteFalloElDisparo = true;
            }
            if (servidorPerdio) {
                partidaTerminada = true;
                System.out.println("Gano " + nombreJugador + ": hundio toda la flota del servidor");
            }
        }
        return partidaTerminada;
    }

    private boolean jugarTurnoDelServidor() throws IOException, ClassNotFoundException {
        int disparosRealizados = 0;
        boolean servidorFalloElDisparo = false;
        boolean partidaTerminada = false;

        while (disparosRealizados < LogicaBarcos.DISPAROS_POR_TURNO && !servidorFalloElDisparo && !partidaTerminada) {
            elegirCasillaParaDisparar();

            Mensaje mensajeDisparo = new Mensaje(Mensaje.TIPO_DISPARO);
            mensajeDisparo.setFila(filaDisparoServidor);
            mensajeDisparo.setColumna(columnaDisparoServidor);
            enviar(mensajeDisparo);

            Mensaje mensajeResultado = recibirMensajeDeTipo(Mensaje.TIPO_RESULTADO);
            String resultado = mensajeResultado.getTexto();

            boolean resultadoValido = LogicaBarcos.RESULTADO_AGUA.equals(resultado)
                    || LogicaBarcos.RESULTADO_TOCADO.equals(resultado)
                    || LogicaBarcos.RESULTADO_HUNDIDO.equals(resultado);
            if (!resultadoValido) {
                throw new IOException("El cliente respondio con un resultado invalido: " + resultado);
            }

            logicaServidor.registrarResultadoDisparo(filaDisparoServidor, columnaDisparoServidor, resultado);
            if (LogicaBarcos.RESULTADO_HUNDIDO.equals(resultado)) {
                if (!mensajeResultado.tieneBarcoHundido()) {
                    throw new IOException("El cliente no indico cual barco se hundio");
                }
                logicaServidor.registrarBarcoEnemigoHundido(mensajeResultado.obtenerBarcoHundido());
            }

            System.out.println("El servidor disparo a " + LogicaBarcos.nombreDeCasilla(filaDisparoServidor, columnaDisparoServidor) + ": " + resultado);
            disparosRealizados++;

            if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
                servidorFalloElDisparo = true;
            }
            if (mensajeResultado.isFinDeJuego()) {
                partidaTerminada = true;
                System.out.println("Gano el servidor: hundio toda la flota de " + nombreJugador);
            }
        }
        return partidaTerminada;
    }

    private void elegirCasillaParaDisparar() {
        char[][] tableroDeTiro = logicaServidor.getTableroDeTiro();
        do {
            filaDisparoServidor = generadorAleatorio.nextInt(LogicaBarcos.TAMANO_TABLERO);
            columnaDisparoServidor = generadorAleatorio.nextInt(LogicaBarcos.TAMANO_TABLERO);
        } while (tableroDeTiro[filaDisparoServidor][columnaDisparoServidor] != LogicaBarcos.CASILLA_AGUA);
    }

    private void mostrarTablerosEnConsola() {
        char[][] tableroPropio = logicaServidor.getTableroPropio();
        char[][] tableroDeTiro = logicaServidor.getTableroDeTiro();

        System.out.println();
        System.out.println("   TABLERO PROPIO DEL SERVIDOR           TABLERO DE TIRO DEL SERVIDOR");

        StringBuilder encabezado = new StringBuilder("  ");
        for (int columna = 1; columna <= LogicaBarcos.TAMANO_TABLERO; columna++) {
            encabezado.append(String.format("%3d", columna));
        }
        System.out.println(encabezado + "      " + encabezado);

        for (int fila = 0; fila < LogicaBarcos.TAMANO_TABLERO; fila++) {
            StringBuilder lineaPropio = new StringBuilder();
            StringBuilder lineaDeTiro = new StringBuilder();
            lineaPropio.append((char) ('A' + fila)).append(" ");
            lineaDeTiro.append((char) ('A' + fila)).append(" ");
            for (int columna = 0; columna < LogicaBarcos.TAMANO_TABLERO; columna++) {
                lineaPropio.append("  ").append(tableroPropio[fila][columna]);
                lineaDeTiro.append("  ").append(tableroDeTiro[fila][columna]);
            }
            System.out.println(lineaPropio + "      " + lineaDeTiro);
        }
        System.out.println();
    }
}