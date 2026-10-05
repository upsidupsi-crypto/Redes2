import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Reproductor de efectos de sonido. Todo es estático: cada archivo .wav
 * se carga UNA sola vez (la primera vez que se pide, o antes con
 * precargar) y se guarda en un caché. Así no se crean clips nuevos en
 * cada partida (antes se acumulaban y podían agotar las líneas de audio).
 *
 * Uso: Sonido.reproducir("click")  ->  carga "sonidos/click.wav"
 *
 * El flag "activado" aplica a TODOS los sonidos (botón de silencio del menú).
 * Se usa solo desde el hilo de Swing, por eso no necesita sincronización.
 */
public class Sonido {

    private static final String CARPETA = "sonidos/";
    private static final Map<String, Clip> cache = new HashMap<>();
    private static boolean activado = true;

    private Sonido() { }

    public static void precargar(String... nombres) {
        for (String nombre : nombres) obtener(nombre);
    }

    public static void reproducir(String nombre) {
        if (!activado) return;
        Clip clip = obtener(nombre);
        if (clip == null) return;
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    private static Clip obtener(String nombre) {
        // containsKey (y no solo get) para no reintentar cargar, ni repetir
        // el mensaje de error, si el archivo no existe.
        if (!cache.containsKey(nombre)) {
            cache.put(nombre, cargar(nombre));
        }
        return cache.get(nombre);
    }

    private static Clip cargar(String nombre) {
        String ruta = CARPETA + nombre + ".wav";
        try {
            AudioInputStream audio = AudioSystem.getAudioInputStream(new File(ruta));
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            return clip;
        } catch (Exception e) {
            System.out.println("No se pudo cargar el sonido " + ruta + ": " + e.getMessage());
            return null;
        }
    }

    public static void setActivado(boolean valor) { activado = valor; }
    public static boolean isActivado() { return activado; }
}
