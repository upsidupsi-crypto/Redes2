import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LogicaBarcos {

    public static final int TAM = 10;

    public static final char AGUA    = '~';
    public static final char BARCO   = 'B';
    public static final char TOCADO  = 'X';
    public static final char FALLO   = 'O';
    public static final char HUNDIDO = '#';

    private final char[][] tableroPropio;
    private final char[][] tableroTiro;
    private final List<Barco> misBarcos;

    public LogicaBarcos() {
        tableroPropio = new char[TAM][TAM];
        tableroTiro   = new char[TAM][TAM];
        for (char[] fila : tableroPropio) java.util.Arrays.fill(fila, AGUA);
        for (char[] fila : tableroTiro)   java.util.Arrays.fill(fila, AGUA);
        misBarcos = new ArrayList<>();
    }

    public boolean colocarBarco(String tipo, int longitud, int fila, int col, boolean horizontal) {
        Barco candidato = new Barco(tipo, longitud);
        candidato.fijarPosiciones(fila, col, horizontal);

        for (int[] pos : candidato.getPosiciones()) {
            int f = pos[0], c = pos[1];
            if (f < 0 || f >= TAM || c < 0 || c >= TAM) return false;
            if (tableroPropio[f][c] != AGUA) return false;
            if (!zonaLibreDeVecinos(f, c)) return false; // barcos no pueden tocarse
        }

        for (int[] pos : candidato.getPosiciones()) {
            tableroPropio[pos[0]][pos[1]] = BARCO;
        }
        misBarcos.add(candidato);
        return true;
    }

    // Revisa las 8 casillas alrededor (y la propia) de una posición candidata
    private boolean zonaLibreDeVecinos(int fila, int col) {
        for (int df = -1; df <= 1; df++) {
            for (int dc = -1; dc <= 1; dc++) {
                int f = fila + df;
                int c = col + dc;
                if (f < 0 || f >= TAM || c < 0 || c >= TAM) continue;
                if (tableroPropio[f][c] == BARCO) return false;
            }
        }
        return true;
    }

    public void colocarBarcosAleatorio() {
        Random rnd = new Random();
        int[] longitudes = {5, 4, 3, 3, 2, 2, 2};
        String[] tipos = {"Submarino", "Acorazado", "Crucero", "Crucero", "Destructor", "Destructor", "Destructor"};

        for (int i = 0; i < longitudes.length; i++) {
            boolean colocado = false;
            while (!colocado) {
                int fila = rnd.nextInt(TAM);
                int col  = rnd.nextInt(TAM);
                boolean horizontal = rnd.nextBoolean();
                colocado = colocarBarco(tipos[i], longitudes[i], fila, col, horizontal);
            }
        }
    }

    // Llamar del lado que RECIBE el disparo, sobre su tableroPropio
    // Retorna "REPETIDO" si esa casilla ya había sido disparada antes
    public String recibirDisparo(int fila, int col) {
        char estadoActual = tableroPropio[fila][col];
        if (estadoActual == TOCADO || estadoActual == FALLO || estadoActual == HUNDIDO) {
            return "REPETIDO";
        }

        Barco impactado = buscarBarcoEn(fila, col);

        if (impactado == null) {
            tableroPropio[fila][col] = FALLO;
            return "AGUA";
        }

        impactado.recibirDisparo(fila, col);
        if (impactado.estaHundido()) {
            for (int[] pos : impactado.getPosiciones()) {
                tableroPropio[pos[0]][pos[1]] = HUNDIDO;
            }
            return "HUNDIDO";
        } else {
            tableroPropio[fila][col] = TOCADO;
            return "TOCADO";
        }
    }

    // Llamar del lado que DISPARÓ, para pintar su tableroTiro con la respuesta
    public void registrarResultadoTiro(int fila, int col, String resultado) {
        switch (resultado) {
            case "AGUA":    tableroTiro[fila][col] = FALLO;   break;
            case "TOCADO":  tableroTiro[fila][col] = TOCADO;  break;
            case "HUNDIDO": tableroTiro[fila][col] = HUNDIDO; break;
            // "REPETIDO" no modifica el tablero de tiro
        }
    }

    private Barco buscarBarcoEn(int fila, int col) {
        for (Barco b : misBarcos) {
            if (b.ocupaCasilla(fila, col)) return b;
        }
        return null;
    }

    public boolean todosHundidos() {
        for (Barco b : misBarcos) {
            if (!b.estaHundido()) return false;
        }
        return true;
    }

    public char[][] getTableroPropio() { return tableroPropio; }
    public char[][] getTableroTiro()   { return tableroTiro; }
    public List<Barco> getMisBarcos()  { return misBarcos; }
}
