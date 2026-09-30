import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Servidor {

    private static final int PUERTO = 5000;
    private final Random random = new Random();

    private LogicaBarcos logicaServidor;
    private ObjectInputStream entrada;
    private ObjectOutputStream salida;

    public static void main(String[] args) {
        new Servidor().iniciar();
    }

    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("Esperando conexión de un cliente...");
            Socket socket = serverSocket.accept(); // solo se acepta un cliente

            // IMPORTANTE: el ObjectOutputStream debe crearse (y hacer flush)
            // ANTES que el ObjectInputStream, en ambos extremos. Si no,
            // los dos lados se quedan esperando y el programa se traba.
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());

            String nombreCliente = recibirNombre();
            System.out.println("Jugador conectado: " + nombreCliente);

            enviar(new Mensaje("INICIO"));

            logicaServidor = new LogicaBarcos();
            logicaServidor.colocarBarcosAleatorio();

            esperarListoDelCliente();

            jugar();

            socket.close();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error de conexión: " + e.getMessage());
        }
    }

    private void enviar(Mensaje m) throws IOException {
        salida.writeObject(m);
        salida.flush();
        salida.reset(); // evita que ObjectOutputStream reenvíe versiones "cacheadas" del objeto
    }

    private Mensaje recibir() throws IOException, ClassNotFoundException {
        return (Mensaje) entrada.readObject();
    }

    private String recibirNombre() throws IOException, ClassNotFoundException {
        Mensaje m = recibir(); // tipo NOMBRE
        return m.getTexto();
    }

    private void esperarListoDelCliente() throws IOException, ClassNotFoundException {
        Mensaje m = recibir(); // tipo LISTO
        System.out.println("Cliente listo: " + m.getTipo());
    }

    private void jugar() throws IOException, ClassNotFoundException {
        boolean turnoCliente = random.nextBoolean();

        Mensaje turno = new Mensaje("TURNO");
        turno.setTexto(turnoCliente ? "CLIENTE" : "SERVIDOR");
        enviar(turno);

        boolean finDeJuego = false;

        while (!finDeJuego) {
            finDeJuego = turnoCliente ? turnoDeCliente() : turnoDeServidor();
            turnoCliente = !turnoCliente;
        }
    }

    // El cliente dispara sobre el tablero del servidor
    private boolean turnoDeCliente() throws IOException, ClassNotFoundException {
        int disparos = 0;
        boolean fallo = false;
        boolean finDeJuego = false;

        while (disparos < 3 && !fallo && !finDeJuego) {
            Mensaje disparo = recibir(); // tipo DISPARO
            String resultado = logicaServidor.recibirDisparo(disparo.getFila(), disparo.getCol());

            if (resultado.equals("REPETIDO")) {
                enviar(construirResultado(resultado, false));
                continue; // no cuenta como disparo válido, se vuelve a pedir sin gastar turno
            }

            boolean servidorPerdio = logicaServidor.todosHundidos();
            enviar(construirResultado(resultado, servidorPerdio));

            if (servidorPerdio) {
                System.out.println("El cliente ganó la partida.");
                finDeJuego = true;
            }
            if (resultado.equals("AGUA")) fallo = true;
            disparos++;
        }
        return finDeJuego;
    }

    // El servidor dispara sobre el tablero del cliente (disparo aleatorio)
    private boolean turnoDeServidor() throws IOException, ClassNotFoundException {
        int disparos = 0;
        boolean fallo = false;
        boolean finDeJuego = false;

        while (disparos < 3 && !fallo && !finDeJuego) {
            int[] coord = elegirDisparoAleatorio();

            Mensaje disparo = new Mensaje("DISPARO");
            disparo.setFila(coord[0]);
            disparo.setCol(coord[1]);
            enviar(disparo);

            Mensaje respuesta = recibir(); // tipo RESULTADO
            String resultado = respuesta.getTexto();
            boolean clientePerdio = respuesta.isFin();

            logicaServidor.registrarResultadoTiro(coord[0], coord[1], resultado);

            if (clientePerdio) {
                System.out.println("El servidor ganó la partida.");
                finDeJuego = true;
            }
            if (resultado.equals("AGUA")) fallo = true;
            disparos++;
        }
        return finDeJuego;
    }

    private Mensaje construirResultado(String resultado, boolean fin) {
        Mensaje m = new Mensaje("RESULTADO");
        m.setTexto(resultado);
        m.setFin(fin);
        return m;
    }

    // Elige una coordenada que el servidor no haya disparado antes
    private int[] elegirDisparoAleatorio() {
        char[][] tableroTiro = logicaServidor.getTableroTiro();
        int fila, col;
        do {
            fila = random.nextInt(LogicaBarcos.TAM);
            col = random.nextInt(LogicaBarcos.TAM);
        } while (tableroTiro[fila][col] != LogicaBarcos.AGUA);
        return new int[]{fila, col};
    }
}
