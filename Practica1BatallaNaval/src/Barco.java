import java.util.ArrayList;
import java.util.List;

public class Barco {

    private final String tipo;
    private final int longitud;
    private final List<int[]> posiciones;
    private final boolean[] golpes;

    public Barco(String tipo, int longitud) {
        this.tipo = tipo;
        this.longitud = longitud;
        this.posiciones = new ArrayList<>();
        this.golpes = new boolean[longitud];
    }

    public void fijarPosiciones(int filaInicio, int colInicio, boolean horizontal) {
        posiciones.clear();
        for (int i = 0; i < longitud; i++) {
            int fila = horizontal ? filaInicio : filaInicio + i;
            int col  = horizontal ? colInicio + i : colInicio;
            posiciones.add(new int[]{fila, col});
        }
    }

    public boolean ocupaCasilla(int fila, int col) {
        for (int[] pos : posiciones) {
            if (pos[0] == fila && pos[1] == col) return true;
        }
        return false;
    }

    public boolean recibirDisparo(int fila, int col) {
        for (int i = 0; i < posiciones.size(); i++) {
            int[] pos = posiciones.get(i);
            if (pos[0] == fila && pos[1] == col) {
                golpes[i] = true;
                return true;
            }
        }
        return false;
    }

    public boolean estaHundido() {
        for (boolean g : golpes) {
            if (!g) return false;
        }
        return true;
    }

    public String getTipo() { return tipo; }
    public int getLongitud() { return longitud; }
    public List<int[]> getPosiciones() { return posiciones; }
}
