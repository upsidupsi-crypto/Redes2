package com.redes.practica2.protocolo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class ReceptorGoBackN {

    private final DatagramSocket socket;
    private final int timeoutPrimerPaqueteMs;
    private final int timeoutInactividadMs;
    private final double probabilidadPerdidaConfirmaciones;
    private final TipoPaquete tipoDatosEsperado;
    private final Random aleatorio = new Random();

    private InetAddress direccionPar;
    private int puertoPar;
    private int tramasAceptadas;
    private int tramasDescartadas;
    private int confirmacionesPerdidas;

    public ReceptorGoBackN(DatagramSocket socket, int timeoutPrimerPaqueteMs, int timeoutInactividadMs,
                           double probabilidadPerdidaConfirmaciones, TipoPaquete tipoDatosEsperado) {
        this.socket = socket;
        this.timeoutPrimerPaqueteMs = timeoutPrimerPaqueteMs;
        this.timeoutInactividadMs = timeoutInactividadMs;
        this.probabilidadPerdidaConfirmaciones = probabilidadPerdidaConfirmaciones;
        this.tipoDatosEsperado = tipoDatosEsperado;
    }

    public long recibir(OutputStream salida) throws IOException {
        byte[] buffer = new byte[Trama.TAMANO_MAXIMO_TRAMA];
        int indiceEsperado = 0;
        int ultimaSecuenciaConfirmada = -1;
        long bytesRecibidos = 0;
        boolean parConocido = false;

        while (true) {
            socket.setSoTimeout(parConocido ? timeoutInactividadMs : timeoutPrimerPaqueteMs);
            DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(paquete);
            } catch (SocketTimeoutException tiempoAgotado) {
                if (!parConocido) {
                    throw tiempoAgotado;
                }
                throw new IOException("La transferencia se interrumpió por inactividad del emisor", tiempoAgotado);
            }
            if (parConocido && (!paquete.getAddress().equals(direccionPar) || paquete.getPort() != puertoPar)) {
                continue;
            }
            Trama trama;
            try {
                trama = Trama.deserializar(paquete.getData(), paquete.getLength());
            } catch (IllegalArgumentException tramaInvalida) {
                continue;
            }
            boolean esRelevante = trama.esTipo(tipoDatosEsperado) || trama.esTipo(TipoPaquete.FIN)
                    || trama.esTipo(TipoPaquete.ERROR);
            if (!esRelevante) {
                continue;
            }
            if (!parConocido) {
                direccionPar = paquete.getAddress();
                puertoPar = paquete.getPort();
                parConocido = true;
            }
            if (trama.esTipo(TipoPaquete.ERROR)) {
                throw new IOException("El servidor respondió con error: "
                        + new String(trama.obtenerCarga(), StandardCharsets.UTF_8));
            }
            if (trama.obtenerNumSecuencia() == indiceEsperado % Trama.MODULO_SECUENCIA) {
                if (trama.esTipo(TipoPaquete.FIN)) {
                    enviarConfirmacion(trama.obtenerNumSecuencia());
                    return bytesRecibidos;
                }
                salida.write(trama.obtenerCarga());
                bytesRecibidos += trama.obtenerTamanoCarga();
                ultimaSecuenciaConfirmada = trama.obtenerNumSecuencia();
                indiceEsperado++;
                tramasAceptadas++;
                enviarConfirmacion(ultimaSecuenciaConfirmada);
            } else {
                tramasDescartadas++;
                if (ultimaSecuenciaConfirmada >= 0) {
                    enviarConfirmacion(ultimaSecuenciaConfirmada);
                }
            }
        }
    }

    private void enviarConfirmacion(int secuencia) throws IOException {
        if (probabilidadPerdidaConfirmaciones > 0 && aleatorio.nextDouble() < probabilidadPerdidaConfirmaciones) {
            confirmacionesPerdidas++;
            return;
        }
        byte[] bytesCrudos = new Trama(TipoPaquete.CONFIRMACION, 0, secuencia, null).serializar();
        socket.send(new DatagramPacket(bytesCrudos, bytesCrudos.length, direccionPar, puertoPar));
    }

    public int obtenerTramasAceptadas() {
        return tramasAceptadas;
    }

    public int obtenerTramasDescartadas() {
        return tramasDescartadas;
    }

    public int obtenerConfirmacionesPerdidas() {
        return confirmacionesPerdidas;
    }
}
