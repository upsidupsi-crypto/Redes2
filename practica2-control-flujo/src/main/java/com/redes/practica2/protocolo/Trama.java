package com.redes.practica2.protocolo;

import java.nio.ByteBuffer;

public class Trama {

    public static final int TAMANO_CABECERA = 13;
    public static final int TAMANO_MAXIMO_CARGA = 1024;
    public static final int TAMANO_MAXIMO_TRAMA = TAMANO_CABECERA + TAMANO_MAXIMO_CARGA;
    public static final int MODULO_SECUENCIA = 256;

    private final byte tipo;
    private final int numSecuencia;
    private final int numConfirmacion;
    private final int tamanoCarga;
    private final byte[] carga;

    public Trama(TipoPaquete tipoPaquete, int numSecuencia, int numConfirmacion, byte[] carga) {
        this.tipo = tipoPaquete.obtenerCodigo();
        this.numSecuencia = numSecuencia;
        this.numConfirmacion = numConfirmacion;
        this.carga = carga == null ? new byte[0] : carga;
        this.tamanoCarga = this.carga.length;
    }

    private Trama(byte tipo, int numSecuencia, int numConfirmacion, int tamanoCarga, byte[] carga) {
        this.tipo = tipo;
        this.numSecuencia = numSecuencia;
        this.numConfirmacion = numConfirmacion;
        this.tamanoCarga = tamanoCarga;
        this.carga = carga;
    }

    public byte[] serializar() {
        ByteBuffer buffer = ByteBuffer.allocate(TAMANO_CABECERA + tamanoCarga);
        buffer.put(tipo);
        buffer.putInt(numSecuencia);
        buffer.putInt(numConfirmacion);
        buffer.putInt(tamanoCarga);
        buffer.put(carga);
        return buffer.array();
    }

    public static Trama deserializar(byte[] bytesCrudos, int longitudValida) {
        if (longitudValida < TAMANO_CABECERA) {
            throw new IllegalArgumentException("Trama incompleta");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytesCrudos, 0, longitudValida);
        byte tipo = buffer.get();
        int numSecuencia = buffer.getInt();
        int numConfirmacion = buffer.getInt();
        int tamanoCarga = buffer.getInt();
        if (tamanoCarga < 0 || tamanoCarga > longitudValida - TAMANO_CABECERA) {
            throw new IllegalArgumentException("Tamaño de carga útil inválido");
        }
        byte[] carga = new byte[tamanoCarga];
        buffer.get(carga);
        return new Trama(tipo, numSecuencia, numConfirmacion, tamanoCarga, carga);
    }

    public byte obtenerTipo() {
        return tipo;
    }

    public TipoPaquete obtenerTipoPaquete() {
        return TipoPaquete.desdeCodigo(tipo);
    }

    public boolean esTipo(TipoPaquete tipoPaquete) {
        return tipo == tipoPaquete.obtenerCodigo();
    }

    public int obtenerNumSecuencia() {
        return numSecuencia;
    }

    public int obtenerNumConfirmacion() {
        return numConfirmacion;
    }

    public int obtenerTamanoCarga() {
        return tamanoCarga;
    }

    public byte[] obtenerCarga() {
        return carga;
    }
}
