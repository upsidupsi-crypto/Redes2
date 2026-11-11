package com.redes.practica2.protocolo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Random;

public class EmisorGoBackN {

    private static final int MAXIMO_TIMEOUTS_CONSECUTIVOS = 25;
    private static final int TIMEOUTS_TOLERADOS_FIN = 5;
    private static final int MILISEGUNDOS_SONDEO_CONFIRMACION = 50;

    private final DatagramSocket socket;
    private final InetAddress direccionPar;
    private final int puertoPar;
    private final int tamanoVentana;
    private final long timeoutMs;
    private final TipoPaquete tipoDatos;
    private final double probabilidadPerdida;
    private final Random aleatorio = new Random();
    private final Object cerrojo = new Object();

    private byte[] datos;
    private int tramasDeDatos;
    private int totalTramas;
    private int base;
    private int siguienteSecuencia;
    private long inicioTemporizador;
    private volatile boolean enEjecucion;
    private volatile IOException falloEscuchaConfirmaciones;

    private int tramasEnviadas;
    private int tramasDescartadas;
    private int tramasRetransmitidas;

    public EmisorGoBackN(DatagramSocket socket, InetAddress direccionPar, int puertoPar, int tamanoVentana,
                         long timeoutMs, TipoPaquete tipoDatos, double probabilidadPerdida) {
        if (tamanoVentana < 1 || tamanoVentana >= Trama.MODULO_SECUENCIA) {
            throw new IllegalArgumentException("El tamaño de ventana debe estar entre 1 y " + (Trama.MODULO_SECUENCIA - 1));
        }
        if (timeoutMs < 1) {
            throw new IllegalArgumentException("El timeout debe ser positivo");
        }
        this.socket = socket;
        this.direccionPar = direccionPar;
        this.puertoPar = puertoPar;
        this.tamanoVentana = tamanoVentana;
        this.timeoutMs = timeoutMs;
        this.tipoDatos = tipoDatos;
        this.probabilidadPerdida = probabilidadPerdida;
    }

    public void enviar(byte[] datosAEnviar) throws IOException, InterruptedException {
        synchronized (cerrojo) {
            datos = datosAEnviar;
            tramasDeDatos = (datos.length + Trama.TAMANO_MAXIMO_CARGA - 1) / Trama.TAMANO_MAXIMO_CARGA;
            totalTramas = tramasDeDatos + 1;
            base = 0;
            siguienteSecuencia = 0;
            inicioTemporizador = System.currentTimeMillis();
            tramasEnviadas = 0;
            tramasDescartadas = 0;
            tramasRetransmitidas = 0;
        }
        falloEscuchaConfirmaciones = null;
        socket.setSoTimeout(MILISEGUNDOS_SONDEO_CONFIRMACION);
        enEjecucion = true;
        Thread hiloConfirmaciones = new Thread(this::escucharConfirmaciones, "gbn-escucha-confirmaciones");
        hiloConfirmaciones.setDaemon(true);
        hiloConfirmaciones.start();
        try {
            ejecutarCicloTransmision();
        } finally {
            enEjecucion = false;
            hiloConfirmaciones.join();
        }
    }

    private void ejecutarCicloTransmision() throws IOException, InterruptedException {
        int ultimaBaseConocida = 0;
        int timeoutsConsecutivos = 0;
        synchronized (cerrojo) {
            while (base < totalTramas) {
                if (falloEscuchaConfirmaciones != null) {
                    throw falloEscuchaConfirmaciones;
                }
                if (base != ultimaBaseConocida) {
                    ultimaBaseConocida = base;
                    timeoutsConsecutivos = 0;
                }
                while (siguienteSecuencia < totalTramas && siguienteSecuencia < base + tamanoVentana) {
                    transmitir(siguienteSecuencia);
                    if (base == siguienteSecuencia) {
                        inicioTemporizador = System.currentTimeMillis();
                    }
                    siguienteSecuencia++;
                }
                long restante = inicioTemporizador + timeoutMs - System.currentTimeMillis();
                if (restante > 0) {
                    cerrojo.wait(restante);
                    continue;
                }
                timeoutsConsecutivos++;
                boolean soloFinPendiente = base == totalTramas - 1;
                if (soloFinPendiente && timeoutsConsecutivos > TIMEOUTS_TOLERADOS_FIN) {
                    break;
                }
                if (timeoutsConsecutivos > MAXIMO_TIMEOUTS_CONSECUTIVOS) {
                    throw new IOException("El receptor dejó de responder");
                }
                for (int indice = base; indice < siguienteSecuencia; indice++) {
                    transmitir(indice);
                    tramasRetransmitidas++;
                }
                inicioTemporizador = System.currentTimeMillis();
            }
        }
    }

    private void transmitir(int indiceAbsoluto) throws IOException {
        if (probabilidadPerdida > 0 && aleatorio.nextDouble() < probabilidadPerdida) {
            tramasDescartadas++;
            return;
        }
        byte[] bytesCrudos = construirTrama(indiceAbsoluto).serializar();
        socket.send(new DatagramPacket(bytesCrudos, bytesCrudos.length, direccionPar, puertoPar));
        tramasEnviadas++;
    }

    private Trama construirTrama(int indiceAbsoluto) {
        int secuencia = indiceAbsoluto % Trama.MODULO_SECUENCIA;
        if (indiceAbsoluto >= tramasDeDatos) {
            return new Trama(TipoPaquete.FIN, secuencia, 0, null);
        }
        int desplazamiento = indiceAbsoluto * Trama.TAMANO_MAXIMO_CARGA;
        int limite = Math.min(desplazamiento + Trama.TAMANO_MAXIMO_CARGA, datos.length);
        return new Trama(tipoDatos, secuencia, 0, Arrays.copyOfRange(datos, desplazamiento, limite));
    }

    private void escucharConfirmaciones() {
        byte[] buffer = new byte[Trama.TAMANO_MAXIMO_TRAMA];
        while (enEjecucion) {
            DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(paquete);
            } catch (SocketTimeoutException tiempoAgotado) {
                continue;
            } catch (IOException fallo) {
                if (enEjecucion) {
                    falloEscuchaConfirmaciones = fallo;
                    synchronized (cerrojo) {
                        cerrojo.notifyAll();
                    }
                }
                return;
            }
            if (!paquete.getAddress().equals(direccionPar) || paquete.getPort() != puertoPar) {
                continue;
            }
            Trama trama;
            try {
                trama = Trama.deserializar(paquete.getData(), paquete.getLength());
            } catch (IllegalArgumentException tramaInvalida) {
                continue;
            }
            if (trama.esTipo(TipoPaquete.CONFIRMACION)) {
                procesarConfirmacion(trama.obtenerNumConfirmacion());
            }
        }
    }

    private void procesarConfirmacion(int numConfirmacion) {
        synchronized (cerrojo) {
            for (int indice = base; indice < siguienteSecuencia; indice++) {
                if (indice % Trama.MODULO_SECUENCIA == numConfirmacion) {
                    base = indice + 1;
                    inicioTemporizador = System.currentTimeMillis();
                    cerrojo.notifyAll();
                    return;
                }
            }
        }
    }

    public int obtenerTramasEnviadas() {
        synchronized (cerrojo) {
            return tramasEnviadas;
        }
    }

    public int obtenerTramasDescartadas() {
        synchronized (cerrojo) {
            return tramasDescartadas;
        }
    }

    public int obtenerTramasRetransmitidas() {
        synchronized (cerrojo) {
            return tramasRetransmitidas;
        }
    }
}
