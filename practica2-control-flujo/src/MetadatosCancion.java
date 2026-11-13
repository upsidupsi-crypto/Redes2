import java.io.Serializable;
import java.nio.file.Path;

public class MetadatosCancion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String titulo;
    private final String artista;
    private final String album;
    private final String genero;
    private final String anio;
    private final int numeroPista;
    private final long duracionSegundos;
    private final long tamanoArchivo;
    private final byte[] caratula;
    private final transient Path rutaArchivo;

    public MetadatosCancion(int id, String titulo, String artista, String album, String genero, String anio,
                            int numeroPista, long duracionSegundos, long tamanoArchivo, byte[] caratula,
                            Path rutaArchivo) {
        this.id = id;
        this.titulo = titulo;
        this.artista = artista;
        this.album = album;
        this.genero = genero;
        this.anio = anio;
        this.numeroPista = numeroPista;
        this.duracionSegundos = duracionSegundos;
        this.tamanoArchivo = tamanoArchivo;
        this.caratula = caratula;
        this.rutaArchivo = rutaArchivo;
    }

    public int obtenerId() {
        return id;
    }

    public String obtenerTitulo() {
        return titulo;
    }

    public String obtenerArtista() {
        return artista;
    }

    public String obtenerAlbum() {
        return album;
    }

    public String obtenerGenero() {
        return genero;
    }

    public String obtenerAnio() {
        return anio;
    }

    public int obtenerNumeroPista() {
        return numeroPista;
    }

    public long obtenerDuracionSegundos() {
        return duracionSegundos;
    }

    public long obtenerTamanoArchivo() {
        return tamanoArchivo;
    }

    public byte[] obtenerCaratula() {
        return caratula;
    }

    public Path obtenerRutaArchivo() {
        return rutaArchivo;
    }

    public String obtenerDuracionFormateada() {
        return String.format("%d:%02d", duracionSegundos / 60, duracionSegundos % 60);
    }

    @Override
    public String toString() {
        return id + " - " + titulo + " (" + artista + ")";
    }
}