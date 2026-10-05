import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Capa de red del lado del cliente. Abre el socket y habla el mismo
 * protocolo de Mensaje que ya usa Servidor.java (NOMBRE/INICIO/LISTO/
 * TURNO/DISPARO/RESULTADO). No sabe nada de Swing ni de LogicaBarcos:
 * solo manda y recibe Mensaje.
 *
 * IMPORTANTE: cada método público de aquí que espera una respuesta del
 * servidor (enviarNombreYEsperarInicio, enviarListoYEsperarTurno,
 * disparar, esperarDisparoEnemigo) BLOQUEA el hilo que lo llama hasta
 * que el dato llega. Por eso PanelJuegoRed los usa siempre desde un
 * hilo aparte (nunca desde el hilo de Swing), igual que el Servidor
 * procesa un jugador a la vez de forma bloqueante.
 */
public class Cliente {

    private final Socket socket;
    private final ObjectOutputStream salida;
    private final ObjectInputStream entrada;

    public Cliente(String host, int puerto) throws IOException {
        socket = new Socket(host, puerto);

        // Mismo orden obligatorio que en Servidor.java: el
        // ObjectOutputStream se crea (y hace flush) ANTES que el
        // ObjectInputStream, en ambos extremos.
        salida = new ObjectOutputStream(socket.getOutputStream());
        salida.flush();
        entrada = new ObjectInputStream(socket.getInputStream());
    }

    private void enviar(Mensaje m) throws IOException {
        salida.writeObject(m);
        salida.flush();
        salida.reset();
    }

    private Mensaje recibir() throws IOException, ClassNotFoundException {
        return (Mensaje) entrada.readObject();
    }

    /** Manda el nombre del jugador y bloquea hasta que llega el mensaje INICIO. */
    public void enviarNombreYEsperarInicio(String nombre) throws IOException, ClassNotFoundException {
        Mensaje m = new Mensaje("NOMBRE");
        m.setTexto(nombre);
        enviar(m);
        recibir(); // INICIO
    }

    /** Avisa que ya se colocó la flota y retorna si el turno inicial es del cliente. */
    public boolean enviarListoYEsperarTurno() throws IOException, ClassNotFoundException {
        enviar(new Mensaje("LISTO"));
        Mensaje turno = recibir(); // TURNO
        return "CLIENTE".equals(turno.getTexto());
    }

    /** Envía un disparo propio y bloquea hasta recibir el resultado. */
    public Mensaje disparar(int fila, int col) throws IOException, ClassNotFoundException {
        Mensaje m = new Mensaje("DISPARO");
        m.setFila(fila);
        m.setCol(col);
        enviar(m);
        return recibir(); // RESULTADO
    }

    /** Bloquea hasta que el servidor dispare sobre nuestro tablero. */
    public Mensaje esperarDisparoEnemigo() throws IOException, ClassNotFoundException {
        return recibir(); // DISPARO
    }

    /** Responde el disparo recibido con el resultado calculado en nuestro propio tablero. */
    public void enviarResultado(String resultado, boolean finDeJuego) throws IOException {
        Mensaje m = new Mensaje("RESULTADO");
        m.setTexto(resultado);
        m.setFin(finDeJuego);
        enviar(m);
    }

    public void cerrar() {
        try {
            socket.close();
        } catch (IOException ignorado) {
            // ya se está cerrando todo, no hay nada que hacer con este error
        }
    }
}
