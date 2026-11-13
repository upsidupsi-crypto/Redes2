import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServidorMultimedia {

    public static final int PUERTO_PREDETERMINADO = 9876;
    private static final int TAMANO_VENTANA_PREDETERMINADO = 8;
    private static final long TIMEOUT_PREDETERMINADO_MS = 200;
    private static final int REPETICIONES_ERROR = 3;

    private final int puerto;
    private final CatalogoCanciones catalogo;
    private final int tamanoVentana;
    private final long timeoutMs;
    private final double probabilidadPerdida;
    private final ExecutorService trabajadores = Executors.newCachedThreadPool();

    public ServidorMultimedia(int puerto, CatalogoCanciones catalogo, int tamanoVentana, long timeoutMs,
                              double probabilidadPerdida) {
        this.puerto = puerto;
        this.catalogo = catalogo;
        this.tamanoVentana = tamanoVentana;
        this.timeoutMs = timeoutMs;
        this.probabilidadPerdida = probabilidadPerdida;
    }

    public static void main(String[] argumentos) {
        try {
            String rutaPorDefecto = "practica2-control-flujo/music";

            Path carpeta = Paths.get(argumentos.length > 0 ? argumentos[0] : rutaPorDefecto);
            int tamanoVentana = argumentos.length > 1 ? Integer.parseInt(argumentos[1]) : TAMANO_VENTANA_PREDETERMINADO;
            long timeoutMs = argumentos.length > 2 ? Long.parseLong(argumentos[2]) : TIMEOUT_PREDETERMINADO_MS;
            double probabilidadPerdida = argumentos.length > 3 ? Double.parseDouble(argumentos[3]) : 0.0;
            int puerto = argumentos.length > 4 ? Integer.parseInt(argumentos[4]) : PUERTO_PREDETERMINADO;

            List<MetadatosCancion> canciones = new AnalizadorID3().analizarDirectorio(carpeta);
            CatalogoCanciones catalogo = new CatalogoCanciones(canciones);
            System.out.println("Canciones indexadas: " + catalogo.tamano());
            System.out.println("Carpeta de origen: " + carpeta.toAbsolutePath());
            System.out.println("Ventana N=" + tamanoVentana + ", timeout=" + timeoutMs + " ms, pérdida simulada="
                    + probabilidadPerdida + ", puerto=" + puerto);
            new ServidorMultimedia(puerto, catalogo, tamanoVentana, timeoutMs, probabilidadPerdida).iniciar();
        } catch (IOException | IllegalArgumentException fallo) {
            System.err.println("No se pudo iniciar el servidor: " + fallo.getMessage());
        }
    }

    public void iniciar() throws IOException {
        try (DatagramSocket escucha = new DatagramSocket(puerto)) {
            System.out.println("Servidor escuchando en el puerto " + puerto);
            byte[] buffer = new byte[Trama.TAMANO_MAXIMO_TRAMA];
            while (true) {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                escucha.receive(paquete);
                Trama solicitud;
                try {
                    solicitud = Trama.deserializar(paquete.getData(), paquete.getLength());
                } catch (IllegalArgumentException tramaInvalida) {
                    continue;
                }
                InetAddress direccionCliente = paquete.getAddress();
                int puertoCliente = paquete.getPort();
                if (solicitud.esTipo(TipoPaquete.SOLICITUD_CATALOGO)) {
                    trabajadores.submit(() -> atenderCatalogo(direccionCliente, puertoCliente));
                } else if (solicitud.esTipo(TipoPaquete.SOLICITUD_ARCHIVO)) {
                    trabajadores.submit(() -> atenderArchivo(solicitud, direccionCliente, puertoCliente));
                }
            }
        } finally {
            trabajadores.shutdownNow();
        }
    }

    private void atenderCatalogo(InetAddress direccionCliente, int puertoCliente) {
        try (DatagramSocket socketTransferencia = new DatagramSocket()) {
            byte[] catalogoSerializado = serializarCatalogo();
            EmisorGoBackN emisor = crearEmisor(socketTransferencia, direccionCliente, puertoCliente,
                    TipoPaquete.RESPUESTA_CATALOGO);
            emisor.enviar(catalogoSerializado);
            System.out.println("Catálogo enviado a " + direccionCliente.getHostAddress() + ":" + puertoCliente
                    + " | tramas=" + emisor.obtenerTramasEnviadas() + " retransmisiones="
                    + emisor.obtenerTramasRetransmitidas() + " perdidas simuladas=" + emisor.obtenerTramasDescartadas());
        } catch (IOException fallo) {
            System.err.println("Fallo al enviar el catálogo: " + fallo.getMessage());
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private void atenderArchivo(Trama solicitud, InetAddress direccionCliente, int puertoCliente) {
        try (DatagramSocket socketTransferencia = new DatagramSocket()) {
            if (solicitud.obtenerTamanoCarga() < Integer.BYTES) {
                enviarError(socketTransferencia, direccionCliente, puertoCliente, "Solicitud de archivo inválida");
                return;
            }
            int idCancion = ByteBuffer.wrap(solicitud.obtenerCarga()).getInt();
            MetadatosCancion cancion = catalogo.buscarPorId(idCancion);
            if (cancion == null || cancion.obtenerRutaArchivo() == null
                    || !Files.isRegularFile(cancion.obtenerRutaArchivo())) {
                enviarError(socketTransferencia, direccionCliente, puertoCliente,
                        "Canción no encontrada: " + idCancion);
                return;
            }
            byte[] contenidoArchivo = Files.readAllBytes(cancion.obtenerRutaArchivo());
            EmisorGoBackN emisor = crearEmisor(socketTransferencia, direccionCliente, puertoCliente, TipoPaquete.DATOS);
            long inicio = System.currentTimeMillis();
            emisor.enviar(contenidoArchivo);
            System.out.println("Archivo '" + cancion.obtenerTitulo() + "' enviado a "
                    + direccionCliente.getHostAddress() + ":" + puertoCliente + " en "
                    + (System.currentTimeMillis() - inicio) + " ms | tramas=" + emisor.obtenerTramasEnviadas()
                    + " retransmisiones=" + emisor.obtenerTramasRetransmitidas()
                    + " perdidas simuladas=" + emisor.obtenerTramasDescartadas());
        } catch (IOException fallo) {
            System.err.println("Fallo al enviar archivo: " + fallo.getMessage());
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private EmisorGoBackN crearEmisor(DatagramSocket socket, InetAddress direccion, int puertoDestino,
                                      TipoPaquete tipoDatos) {
        return new EmisorGoBackN(socket, direccion, puertoDestino, tamanoVentana, timeoutMs, tipoDatos,
                probabilidadPerdida);
    }

    private byte[] serializarCatalogo() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream flujoObjetos = new ObjectOutputStream(buffer)) {
            flujoObjetos.writeObject(catalogo.obtenerTodasLasCanciones().toArray(new MetadatosCancion[0]));
        }
        return buffer.toByteArray();
    }

    private void enviarError(DatagramSocket socket, InetAddress direccion, int puertoDestino, String mensaje)
            throws IOException {
        byte[] bytesCrudos = new Trama(TipoPaquete.ERROR, 0, 0, mensaje.getBytes(StandardCharsets.UTF_8)).serializar();
        for (int repeticion = 0; repeticion < REPETICIONES_ERROR; repeticion++) {
            socket.send(new DatagramPacket(bytesCrudos, bytesCrudos.length, direccion, puertoDestino));
        }
    }
}