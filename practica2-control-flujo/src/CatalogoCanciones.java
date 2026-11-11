import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CatalogoCanciones {

    private final List<MetadatosCancion> canciones;
    private final Map<Integer, MetadatosCancion> cancionesPorId = new LinkedHashMap<>();
    private final Map<String, List<MetadatosCancion>> cancionesPorGenero = new TreeMap<>();
    private final Map<String, List<MetadatosCancion>> cancionesPorArtista = new TreeMap<>();

    public CatalogoCanciones(Collection<MetadatosCancion> canciones) {
        this.canciones = new ArrayList<>(canciones);
        for (MetadatosCancion cancion : this.canciones) {
            cancionesPorId.put(cancion.obtenerId(), cancion);
            cancionesPorGenero.computeIfAbsent(normalizar(cancion.obtenerGenero()), clave -> new ArrayList<>()).add(cancion);
            cancionesPorArtista.computeIfAbsent(normalizar(cancion.obtenerArtista()), clave -> new ArrayList<>()).add(cancion);
        }
    }

    public List<MetadatosCancion> obtenerTodasLasCanciones() {
        return Collections.unmodifiableList(canciones);
    }

    public MetadatosCancion buscarPorId(int id) {
        return cancionesPorId.get(id);
    }

    public List<MetadatosCancion> buscarPorTitulo(String consulta) {
        String consultaNormalizada = normalizar(consulta);
        return canciones.stream()
                .filter(cancion -> normalizar(cancion.obtenerTitulo()).contains(consultaNormalizada))
                .collect(Collectors.toList());
    }

    public List<MetadatosCancion> filtrarPorArtista(String consulta) {
        return recolectarDesdeIndice(cancionesPorArtista, consulta);
    }

    public List<MetadatosCancion> filtrarPorGenero(String consulta) {
        return recolectarDesdeIndice(cancionesPorGenero, consulta);
    }

    public List<String> obtenerArtistas() {
        TreeSet<String> artistas = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        canciones.forEach(cancion -> artistas.add(cancion.obtenerArtista()));
        return new ArrayList<>(artistas);
    }

    public List<String> obtenerGeneros() {
        TreeSet<String> generos = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        canciones.forEach(cancion -> generos.add(cancion.obtenerGenero()));
        return new ArrayList<>(generos);
    }

    public int tamano() {
        return canciones.size();
    }

    private List<MetadatosCancion> recolectarDesdeIndice(Map<String, List<MetadatosCancion>> indice, String consulta) {
        String consultaNormalizada = normalizar(consulta);
        List<MetadatosCancion> coincidencias = new ArrayList<>();
        for (Map.Entry<String, List<MetadatosCancion>> entrada : indice.entrySet()) {
            if (entrada.getKey().contains(consultaNormalizada)) {
                coincidencias.addAll(entrada.getValue());
            }
        }
        coincidencias.sort(Comparator.comparingInt(MetadatosCancion::obtenerId));
        return coincidencias;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}