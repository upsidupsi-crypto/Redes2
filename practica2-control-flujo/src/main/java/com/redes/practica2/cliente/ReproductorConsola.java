package com.redes.practica2.cliente;

import com.redes.practica2.metadatos.MetadatosCancion;
import com.redes.practica2.multimedia.ReproductorAudio;
import com.redes.practica2.servidor.CatalogoCanciones;
import javazoom.jl.decoder.JavaLayerException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

public class ReproductorConsola {

    private static final String HOST_PREDETERMINADO = "localhost";
    private static final int MILISEGUNDOS_SONDEO_REPRODUCCION = 100;

    private final ClienteMultimedia cliente;
    private final ReproductorAudio reproductorAudio = new ReproductorAudio();
    private final BufferedReader lector = new BufferedReader(new InputStreamReader(System.in));
    private CatalogoCanciones catalogo;
    private List<MetadatosCancion> resultadosActuales;

    public ReproductorConsola(ClienteMultimedia cliente) {
        this.cliente = cliente;
    }

    public static void main(String[] argumentos) {
        try {
            String host = argumentos.length > 0 ? argumentos[0] : HOST_PREDETERMINADO;
            int puerto = argumentos.length > 1 ? Integer.parseInt(argumentos[1]) : 9876;
            double probabilidadPerdidaConfirmaciones = argumentos.length > 2 ? Double.parseDouble(argumentos[2]) : 0.0;
            new ReproductorConsola(new ClienteMultimedia(host, puerto, probabilidadPerdidaConfirmaciones)).ejecutar();
        } catch (IOException | IllegalArgumentException fallo) {
            System.err.println("No se pudo iniciar el cliente: " + fallo.getMessage());
        }
    }

    public void ejecutar() throws IOException {
        if (!cargarCatalogo()) {
            return;
        }
        boolean salir = false;
        while (!salir) {
            imprimirMenu();
            String opcion = solicitar("Opción: ");
            if (opcion == null) {
                break;
            }
            switch (opcion.trim()) {
                case "1" -> mostrarTodasLasCanciones();
                case "2" -> buscarPorTitulo();
                case "3" -> filtrarPorArtista();
                case "4" -> filtrarPorGenero();
                case "5" -> seleccionarYReproducir();
                case "6" -> cargarCatalogo();
                case "0" -> salir = true;
                default -> System.out.println("Opción inválida");
            }
        }
        reproductorAudio.detener();
        System.out.println("Hasta luego");
    }

    private boolean cargarCatalogo() {
        System.out.println("Solicitando catálogo al servidor...");
        try {
            catalogo = new CatalogoCanciones(cliente.solicitarCatalogo());
            resultadosActuales = catalogo.obtenerTodasLasCanciones();
            System.out.println("Catálogo recibido: " + catalogo.tamano() + " canciones");
            return true;
        } catch (IOException | ClassNotFoundException fallo) {
            System.err.println("No se pudo obtener el catálogo: " + fallo.getMessage());
            return false;
        }
    }

    private void imprimirMenu() {
        System.out.println();
        System.out.println("===== REPRODUCTOR MP3 =====");
        System.out.println("1. Ver todas las canciones");
        System.out.println("2. Buscar canciones por Título");
        System.out.println("3. Filtrar canciones por Artista");
        System.out.println("4. Filtrar canciones por Género");
        System.out.println("5. Seleccionar canción, descargarla y reproducirla");
        System.out.println("6. Actualizar catálogo");
        System.out.println("0. Salir");
    }

    private void mostrarTodasLasCanciones() {
        resultadosActuales = catalogo.obtenerTodasLasCanciones();
        imprimirCanciones(resultadosActuales);
    }

    private void buscarPorTitulo() throws IOException {
        String consulta = solicitar("Título a buscar: ");
        if (consulta == null || consulta.isBlank()) {
            return;
        }
        resultadosActuales = catalogo.buscarPorTitulo(consulta);
        imprimirCanciones(resultadosActuales);
    }

    private void filtrarPorArtista() throws IOException {
        System.out.println("Artistas disponibles: " + String.join(", ", catalogo.obtenerArtistas()));
        String consulta = solicitar("Artista: ");
        if (consulta == null || consulta.isBlank()) {
            return;
        }
        resultadosActuales = catalogo.filtrarPorArtista(consulta);
        imprimirCanciones(resultadosActuales);
    }

    private void filtrarPorGenero() throws IOException {
        System.out.println("Géneros disponibles: " + String.join(", ", catalogo.obtenerGeneros()));
        String consulta = solicitar("Género: ");
        if (consulta == null || consulta.isBlank()) {
            return;
        }
        resultadosActuales = catalogo.filtrarPorGenero(consulta);
        imprimirCanciones(resultadosActuales);
    }

    private void seleccionarYReproducir() throws IOException {
        imprimirCanciones(resultadosActuales.isEmpty() ? catalogo.obtenerTodasLasCanciones() : resultadosActuales);
        String entrada = solicitar("ID de la canción (Enter para cancelar): ");
        if (entrada == null || entrada.isBlank()) {
            return;
        }
        MetadatosCancion cancion;
        try {
            cancion = catalogo.buscarPorId(Integer.parseInt(entrada.trim()));
        } catch (NumberFormatException numeroInvalido) {
            System.out.println("ID inválido");
            return;
        }
        if (cancion == null) {
            System.out.println("No existe una canción con ese ID");
            return;
        }
        reproductorAudio.detener();
        System.out.println("Descargando '" + cancion.obtenerTitulo() + "' con Go-Back-N...");
        long inicio = System.currentTimeMillis();
        Path archivo;
        try {
            archivo = cliente.descargarCancion(cancion);
        } catch (IOException fallo) {
            System.err.println("Descarga fallida: " + fallo.getMessage());
            return;
        }
        System.out.println("Descarga completada en " + (System.currentTimeMillis() - inicio) + " ms");
        try {
            reproductorAudio.reproducir(archivo);
        } catch (JavaLayerException | IOException fallo) {
            System.err.println("No se pudo reproducir: " + fallo.getMessage());
            return;
        }
        System.out.println("Reproduciendo: " + cancion.obtenerTitulo() + " - " + cancion.obtenerArtista());
        controlarReproduccion();
    }

    private void controlarReproduccion() throws IOException {
        System.out.println("Controles: [p] pausar/reanudar  [s] detener");
        while (reproductorAudio.estaActivo()) {
            if (lector.ready()) {
                String comando = lector.readLine();
                if (comando == null) {
                    reproductorAudio.detener();
                    return;
                }
                switch (comando.trim().toLowerCase()) {
                    case "p" -> {
                        if (reproductorAudio.estaPausado()) {
                            reproductorAudio.reanudar();
                            System.out.println("Reproducción reanudada");
                        } else {
                            reproductorAudio.pausar();
                            System.out.println("Reproducción en pausa");
                        }
                    }
                    case "s" -> {
                        reproductorAudio.detener();
                        System.out.println("Reproducción detenida");
                        return;
                    }
                    default -> System.out.println("Comando inválido");
                }
            } else {
                try {
                    Thread.sleep(MILISEGUNDOS_SONDEO_REPRODUCCION);
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                    reproductorAudio.detener();
                    return;
                }
            }
        }
        System.out.println("Reproducción finalizada");
    }

    private void imprimirCanciones(List<MetadatosCancion> canciones) {
        if (canciones.isEmpty()) {
            System.out.println("Sin resultados");
            return;
        }
        System.out.printf("%-4s %-32s %-22s %-14s %-8s%n", "ID", "Título", "Artista", "Género", "Duración");
        for (MetadatosCancion cancion : canciones) {
            System.out.printf("%-4d %-32s %-22s %-14s %-8s%n", cancion.obtenerId(),
                    truncar(cancion.obtenerTitulo(), 32), truncar(cancion.obtenerArtista(), 22),
                    truncar(cancion.obtenerGenero(), 14), cancion.obtenerDuracionFormateada());
        }
    }

    private String truncar(String texto, int longitudMaxima) {
        return texto.length() <= longitudMaxima ? texto : texto.substring(0, longitudMaxima - 1) + "…";
    }

    private String solicitar(String mensaje) throws IOException {
        System.out.print(mensaje);
        return lector.readLine();
    }
}
