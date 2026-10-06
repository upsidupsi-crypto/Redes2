import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Cliente {

    private static final int TIEMPO_ESPERA_CONEXION_MILISEGUNDOS = 5000;

    private final Socket socket;
    private final ObjectOutputStream flujoSalida;
    private final ObjectInputStream flujoEntrada;

    public Cliente(String direccionServidor, int puertoServidor) throws IOException {
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(direccionServidor, puertoServidor), TIEMPO_ESPERA_CONEXION_MILISEGUNDOS);
            socket.setSoTimeout(TIEMPO_ESPERA_CONEXION_MILISEGUNDOS);
            flujoSalida = new ObjectOutputStream(socket.getOutputStream());
            flujoSalida.flush();
            flujoEntrada = new ObjectInputStream(socket.getInputStream());
        } catch (IOException excepcion) {
            socket.close();
            throw excepcion;
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

    public void enviarNombreYEsperarInicio(String nombreJugador) throws IOException, ClassNotFoundException {
        Mensaje mensajeNombre = new Mensaje(Mensaje.TIPO_NOMBRE);
        mensajeNombre.setTexto(nombreJugador);
        enviar(mensajeNombre);

        Object objetoRecibido = flujoEntrada.readObject();
        if (!(objetoRecibido instanceof Mensaje)) {
            throw new IOException("El servidor respondio con datos desconocidos");
        }
        Mensaje respuesta = (Mensaje) objetoRecibido;

        if (Mensaje.TIPO_OCUPADO.equals(respuesta.getTipo())) {
            throw new IOException("El servidor ya tiene una partida en curso con otro jugador.");
        }
        if (!Mensaje.TIPO_INICIO.equals(respuesta.getTipo())) {
            throw new IOException("El servidor respondio con un mensaje inesperado: " + respuesta.getTipo());
        }
        socket.setSoTimeout(0);
    }

    public boolean enviarListoYEsperarTurno() throws IOException, ClassNotFoundException {
        enviar(new Mensaje(Mensaje.TIPO_LISTO));
        Mensaje mensajeTurno = recibirMensajeDeTipo(Mensaje.TIPO_TURNO);
        return Mensaje.TURNO_CLIENTE.equals(mensajeTurno.getTexto());
    }

    public Mensaje disparar(int fila, int columna) throws IOException, ClassNotFoundException {
        Mensaje mensajeDisparo = new Mensaje(Mensaje.TIPO_DISPARO);
        mensajeDisparo.setFila(fila);
        mensajeDisparo.setColumna(columna);
        enviar(mensajeDisparo);
        return recibirMensajeDeTipo(Mensaje.TIPO_RESULTADO);
    }

    public Mensaje esperarDisparoDelServidor() throws IOException, ClassNotFoundException {
        return recibirMensajeDeTipo(Mensaje.TIPO_DISPARO);
    }

    public void enviarResultado(String resultado, boolean finDeJuego, LogicaBarcos.Barco barcoHundido) throws IOException {
        Mensaje mensajeResultado = new Mensaje(Mensaje.TIPO_RESULTADO);
        mensajeResultado.setTexto(resultado);
        mensajeResultado.setFinDeJuego(finDeJuego);
        if (barcoHundido != null) {
            mensajeResultado.agregarBarcoHundido(barcoHundido);
        }
        enviar(mensajeResultado);
    }

    public void cerrar() {
        try {
            socket.close();
        } catch (IOException excepcion) {
            System.out.println("No se pudo cerrar la conexion: " + excepcion.getMessage());
        }
    }
}