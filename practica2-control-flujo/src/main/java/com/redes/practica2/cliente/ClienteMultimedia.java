package com.redes.practica2.cliente;

import com.redes.practica2.metadatos.MetadatosCancion;
import com.redes.practica2.protocolo.ReceptorGoBackN;
import com.redes.practica2.protocolo.TipoPaquete;
import com.redes.practica2.protocolo.Trama;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ClienteMultimedia {

    private static final int INTENTOS_SOLICITUD = 3;
    private static final int TIMEOUT_PRIMER_PAQUETE_MS = 3000;
    private static final int TIMEOUT_INACTIVIDAD_MS = 8000;

    private final InetAddress direccionServidor;
    private final int puertoServidor;
    private final double probabilidadPerdidaConfirmaciones;
    private final Path directorioDescargas;

    public ClienteMultimedia(String host, int puertoServidor, double probabilidadPerdidaConfirmaciones)
            throws IOException {
        this.direccionServidor = InetAddress.getByName(host);
        this.puertoServidor = puertoServidor;
        this.probabilidadPerdidaConfirmaciones = probabilidadPerdidaConfirmaciones;
        this.directorioDescargas = Files.createTempDirectory("practica2-cliente");
        this.directorioDescargas.toFile().deleteOnExit();
    }

    public List<MetadatosCancion> solicitarCatalogo() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream recibido = new ByteArrayOutputStream();
        Trama solicitud = new Trama(TipoPaquete.SOLICITUD_CATALOGO, 0, 0, null);
        ejecutarTransferencia(solicitud, TipoPaquete.RESPUESTA_CATALOGO, recibido);
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(recibido.toByteArray()))) {
            MetadatosCancion[] canciones = (MetadatosCancion[]) entrada.readObject();
            return Arrays.asList(canciones);
        }
    }

    public Path descargarCancion(MetadatosCancion cancion) throws IOException {
        Path destino = directorioDescargas.resolve("cancion-" + cancion.obtenerId() + ".mp3");
        if (Files.isRegularFile(destino) && Files.size(destino) == cancion.obtenerTamanoArchivo()) {
            return destino;
        }
        byte[] carga = ByteBuffer.allocate(Integer.BYTES).putInt(cancion.obtenerId()).array();
        Trama solicitud = new Trama(TipoPaquete.SOLICITUD_ARCHIVO, 0, 0, carga);
        try (OutputStream salida = new BufferedOutputStream(Files.newOutputStream(destino))) {
            ejecutarTransferencia(solicitud, TipoPaquete.DATOS, salida);
        } catch (IOException fallo) {
            Files.deleteIfExists(destino);
            throw fallo;
        }
        destino.toFile().deleteOnExit();
        return destino;
    }

    private long ejecutarTransferencia(Trama solicitud, TipoPaquete tipoDatosEsperado, OutputStream salida)
            throws IOException {
        SocketTimeoutException ultimoTimeout = null;
        for (int intento = 1; intento <= INTENTOS_SOLICITUD; intento++) {
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] bytesCrudos = solicitud.serializar();
                socket.send(new DatagramPacket(bytesCrudos, bytesCrudos.length, direccionServidor, puertoServidor));
                ReceptorGoBackN receptor = new ReceptorGoBackN(socket, TIMEOUT_PRIMER_PAQUETE_MS,
                        TIMEOUT_INACTIVIDAD_MS, probabilidadPerdidaConfirmaciones, tipoDatosEsperado);
                return receptor.recibir(salida);
            } catch (SocketTimeoutException tiempoAgotado) {
                ultimoTimeout = tiempoAgotado;
            }
        }
        throw new IOException("El servidor no respondió", ultimoTimeout);
    }
}
