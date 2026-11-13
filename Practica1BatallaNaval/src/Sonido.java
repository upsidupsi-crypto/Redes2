import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Sonido {

    private static final String CARPETA_SONIDOS = resolverCarpetaSonidos();
    private static final Map<String, Clip> clipsCargados = new HashMap<String, Clip>();
    private static boolean sonidoActivado = true;

    private Sonido() {
    }

    private static String resolverCarpetaSonidos() {
        File relativa = new File("sonidos");
        if (relativa.isDirectory()) {
            return relativa.getPath() + File.separator;
        }
        try {
            File ubicacion = new File(Sonido.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File directorio = ubicacion;
            while (directorio != null) {
                File candidata = new File(directorio, "sonidos");
                if (candidata.isDirectory()) {
                    return candidata.getPath() + File.separator;
                }
                directorio = directorio.getParentFile();
            }
        } catch (Exception excepcion) {
            System.out.println("No se pudo localizar la carpeta de sonidos: " + excepcion.getMessage());
        }
        return "sonidos" + File.separator;
    }

    public static void precargar(String[] nombresDeSonidos) {
        for (String nombreSonido : nombresDeSonidos) {
            obtenerClip(nombreSonido);
        }
    }

    public static void reproducir(String nombreSonido) {
        if (!sonidoActivado) {
            return;
        }
        Clip clip = obtenerClip(nombreSonido);
        if (clip == null) {
            return;
        }
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    public static void setSonidoActivado(boolean valor) {
        sonidoActivado = valor;
    }

    public static boolean isSonidoActivado() {
        return sonidoActivado;
    }

    private static Clip obtenerClip(String nombreSonido) {
        if (!clipsCargados.containsKey(nombreSonido)) {
            clipsCargados.put(nombreSonido, cargarClip(nombreSonido));
        }
        return clipsCargados.get(nombreSonido);
    }

    private static Clip cargarClip(String nombreSonido) {
        String rutaArchivo = CARPETA_SONIDOS + nombreSonido + ".wav";
        try {
            AudioInputStream flujoDeAudio = AudioSystem.getAudioInputStream(new File(rutaArchivo));
            Clip clip = AudioSystem.getClip();
            clip.open(flujoDeAudio);
            flujoDeAudio.close();
            return clip;
        } catch (Exception excepcion) {
            System.out.println("No se pudo cargar el sonido " + rutaArchivo + ": " + excepcion.getMessage());
            return null;
        }
    }
}