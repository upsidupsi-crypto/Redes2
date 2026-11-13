import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AnalizadorID3 {

    private static final String VALOR_DESCONOCIDO = "Desconocido";

    public List<MetadatosCancion> analizarDirectorio(Path directorio) throws IOException {
        if (!Files.isDirectory(directorio)) {
            throw new IOException("La carpeta de música no existe: " + directorio.toAbsolutePath());
        }
        List<Path> archivosMp3;
        try (Stream<Path> entradas = Files.list(directorio)) {
            archivosMp3 = entradas
                    .filter(Files::isRegularFile)
                    .filter(ruta -> ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
                    .sorted(Comparator.comparing(ruta -> ruta.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        List<MetadatosCancion> canciones = new ArrayList<>();
        int siguienteId = 1;
        for (Path archivoMp3 : archivosMp3) {
            canciones.add(analizarArchivo(siguienteId++, archivoMp3));
        }
        return canciones;
    }

    public MetadatosCancion analizarArchivo(int id, Path archivo) throws IOException {
        long tamanoArchivo = Files.size(archivo);
        String tituloAlternativo = quitarExtension(archivo.getFileName().toString());
        try {
            Mp3File archivoMp3 = new Mp3File(archivo.toString());
            long duracion = archivoMp3.getLengthInSeconds();
            byte[] caratula = null;
            if (archivoMp3.hasId3v2Tag()) {
                ID3v2 etiqueta = archivoMp3.getId3v2Tag();
                caratula = etiqueta.getAlbumImage();
                return new MetadatosCancion(id,
                        valorOPredeterminado(etiqueta.getTitle(), tituloAlternativo),
                        valorOPredeterminado(etiqueta.getArtist(), VALOR_DESCONOCIDO),
                        valorOPredeterminado(etiqueta.getAlbum(), VALOR_DESCONOCIDO),
                        valorOPredeterminado(etiqueta.getGenreDescription(), VALOR_DESCONOCIDO),
                        valorOPredeterminado(etiqueta.getYear(), VALOR_DESCONOCIDO),
                        numeroDePista(archivo),
                        duracion, tamanoArchivo, caratula, archivo);
            }
            if (archivoMp3.hasId3v1Tag()) {
                ID3v1 etiqueta = archivoMp3.getId3v1Tag();
                return new MetadatosCancion(id,
                        valorOPredeterminado(etiqueta.getTitle(), tituloAlternativo),
                        valorOPredeterminado(etiqueta.getArtist(), VALOR_DESCONOCIDO),
                        valorOPredeterminado(etiqueta.getAlbum(), VALOR_DESCONOCIDO),
                        valorOPredeterminado(etiqueta.getGenreDescription(), VALOR_DESCONOCIDO),
                        valorOPredeterminado(etiqueta.getYear(), VALOR_DESCONOCIDO),
                        numeroDePista(archivo),
                        duracion, tamanoArchivo, caratula, archivo);
            }
            return new MetadatosCancion(id, tituloAlternativo, VALOR_DESCONOCIDO, VALOR_DESCONOCIDO,
                    VALOR_DESCONOCIDO, VALOR_DESCONOCIDO, numeroDePista(archivo), duracion, tamanoArchivo,
                    caratula, archivo);
        } catch (UnsupportedTagException | InvalidDataException archivoIlegible) {
            return new MetadatosCancion(id, tituloAlternativo, VALOR_DESCONOCIDO, VALOR_DESCONOCIDO,
                    VALOR_DESCONOCIDO, VALOR_DESCONOCIDO, numeroDePista(archivo), 0, tamanoArchivo, null, archivo);
        }
    }

    private static String valorOPredeterminado(String valor, String valorPredeterminado) {
        if (valor == null || valor.isBlank()) {
            return valorPredeterminado;
        }
        return valor.trim();
    }

    private static String quitarExtension(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        return punto > 0 ? nombreArchivo.substring(0, punto) : nombreArchivo;
    }

    private static int numeroDePista(Path archivo) {
        String nombre = archivo.getFileName().toString().trim();
        int limite = 0;
        while (limite < nombre.length() && limite < 3 && Character.isDigit(nombre.charAt(limite))) {
            limite++;
        }
        if (limite == 0) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(nombre.substring(0, limite));
    }
}