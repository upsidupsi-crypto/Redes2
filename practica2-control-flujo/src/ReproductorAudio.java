import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReproductorAudio {

    private static final long MILISEGUNDOS_ESPERA_DETENCION = 3000;

    private final Object cerrojoEstado = new Object();
    private Thread hiloReproduccion;
    private volatile boolean pausado;
    private volatile boolean detencionSolicitada;

    public void reproducir(Path archivo) throws IOException, JavaLayerException {
        detener();
        InputStream entrada = new BufferedInputStream(Files.newInputStream(archivo));
        Player reproductor = crearReproductor(entrada);
        synchronized (cerrojoEstado) {
            pausado = false;
            detencionSolicitada = false;
            hiloReproduccion = new Thread(() -> ejecutarReproduccion(reproductor, entrada), "reproductor-audio");
            hiloReproduccion.setDaemon(true);
            hiloReproduccion.start();
        }
    }

    private Player crearReproductor(InputStream entrada) throws IOException, JavaLayerException {
        try {
            return new Player(entrada);
        } catch (JavaLayerException | RuntimeException fallo) {
            entrada.close();
            throw fallo;
        }
    }

    private void ejecutarReproduccion(Player reproductor, InputStream entrada) {
        try {
            boolean quedanTramas = true;
            while (quedanTramas && !detencionSolicitada) {
                synchronized (cerrojoEstado) {
                    while (pausado && !detencionSolicitada) {
                        cerrojoEstado.wait();
                    }
                }
                if (detencionSolicitada) {
                    break;
                }
                quedanTramas = reproductor.play(1);
            }
        } catch (JavaLayerException fallo) {
            System.err.println("Error de reproducción: " + fallo.getMessage());
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        } finally {
            reproductor.close();
            try {
                entrada.close();
            } catch (IOException ignorada) {
            }
        }
    }

    public void pausar() {
        if (estaActivo()) {
            pausado = true;
        }
    }

    public void reanudar() {
        synchronized (cerrojoEstado) {
            pausado = false;
            cerrojoEstado.notifyAll();
        }
    }

    public void detener() {
        Thread hilo;
        synchronized (cerrojoEstado) {
            detencionSolicitada = true;
            pausado = false;
            cerrojoEstado.notifyAll();
            hilo = hiloReproduccion;
        }
        if (hilo != null && hilo != Thread.currentThread()) {
            try {
                hilo.join(MILISEGUNDOS_ESPERA_DETENCION);
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean estaActivo() {
        Thread hilo;
        synchronized (cerrojoEstado) {
            hilo = hiloReproduccion;
        }
        return hilo != null && hilo.isAlive();
    }

    public boolean estaPausado() {
        return pausado && estaActivo();
    }
}