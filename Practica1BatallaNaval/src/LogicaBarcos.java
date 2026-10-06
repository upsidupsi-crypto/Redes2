import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class LogicaBarcos {

    public static final int TAMANO_TABLERO = 10;
    public static final int DISPAROS_POR_TURNO = 3;

    public static final char CASILLA_AGUA = '~';
    public static final char CASILLA_BARCO = 'B';
    public static final char CASILLA_TOCADA = 'X';
    public static final char CASILLA_FALLO = 'O';
    public static final char CASILLA_HUNDIDA = '#';

    public static final String RESULTADO_AGUA = "AGUA";
    public static final String RESULTADO_TOCADO = "TOCADO";
    public static final String RESULTADO_HUNDIDO = "HUNDIDO";
    public static final String RESULTADO_REPETIDO = "REPETIDO";
    public static final String RESULTADO_INVALIDO = "INVALIDO";

    public static final String[] NOMBRES_BARCOS = {"Submarino", "Acorazado", "Crucero", "Crucero", "Destructor", "Destructor", "Destructor"};
    public static final int[] LONGITUDES_BARCOS = {5, 4, 3, 3, 2, 2, 2};

    public static class Barco {

        private final String nombre;
        private final int longitud;
        private final boolean horizontal;
        private final int filaInicial;
        private final int columnaInicial;
        private final boolean[] casillasImpactadas;

        public Barco(String nombre, int longitud, int filaInicial, int columnaInicial, boolean horizontal) {
            this.nombre = nombre;
            this.longitud = longitud;
            this.filaInicial = filaInicial;
            this.columnaInicial = columnaInicial;
            this.horizontal = horizontal;
            this.casillasImpactadas = new boolean[longitud];
        }

        public int getFilaDeCasilla(int indiceCasilla) {
            if (horizontal) {
                return filaInicial;
            }
            return filaInicial + indiceCasilla;
        }

        public int getColumnaDeCasilla(int indiceCasilla) {
            if (horizontal) {
                return columnaInicial + indiceCasilla;
            }
            return columnaInicial;
        }

        public boolean ocupaCasilla(int fila, int columna) {
            for (int indiceCasilla = 0; indiceCasilla < longitud; indiceCasilla++) {
                if (getFilaDeCasilla(indiceCasilla) == fila && getColumnaDeCasilla(indiceCasilla) == columna) {
                    return true;
                }
            }
            return false;
        }

        public void registrarImpacto(int fila, int columna) {
            for (int indiceCasilla = 0; indiceCasilla < longitud; indiceCasilla++) {
                if (getFilaDeCasilla(indiceCasilla) == fila && getColumnaDeCasilla(indiceCasilla) == columna) {
                    casillasImpactadas[indiceCasilla] = true;
                }
            }
        }

        public boolean estaHundido() {
            for (boolean casillaImpactada : casillasImpactadas) {
                if (!casillaImpactada) {
                    return false;
                }
            }
            return true;
        }

        public String getNombre() {
            return nombre;
        }

        public int getLongitud() {
            return longitud;
        }

        public boolean isHorizontal() {
            return horizontal;
        }

        public int getFilaInicial() {
            return filaInicial;
        }

        public int getColumnaInicial() {
            return columnaInicial;
        }
    }

    private final char[][] tableroPropio;
    private final char[][] tableroDeTiro;
    private final List<Barco> barcosPropios;
    private final List<Barco> barcosEnemigosHundidos;
    private final Random generadorAleatorio;
    private Barco barcoHundidoEnUltimoDisparo;

    public LogicaBarcos() {
        tableroPropio = new char[TAMANO_TABLERO][TAMANO_TABLERO];
        tableroDeTiro = new char[TAMANO_TABLERO][TAMANO_TABLERO];
        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            Arrays.fill(tableroPropio[fila], CASILLA_AGUA);
            Arrays.fill(tableroDeTiro[fila], CASILLA_AGUA);
        }
        barcosPropios = new ArrayList<Barco>();
        barcosEnemigosHundidos = new ArrayList<Barco>();
        generadorAleatorio = new Random();
        barcoHundidoEnUltimoDisparo = null;
    }

    public static boolean esCasillaValida(int fila, int columna) {
        return fila >= 0 && fila < TAMANO_TABLERO && columna >= 0 && columna < TAMANO_TABLERO;
    }

    public static String nombreDeCasilla(int fila, int columna) {
        return "" + (char) ('A' + fila) + (columna + 1);
    }

    public boolean colocarBarco(String nombre, int longitud, int filaInicial, int columnaInicial, boolean horizontal) {
        Barco barcoNuevo = new Barco(nombre, longitud, filaInicial, columnaInicial, horizontal);

        for (int indiceCasilla = 0; indiceCasilla < longitud; indiceCasilla++) {
            int fila = barcoNuevo.getFilaDeCasilla(indiceCasilla);
            int columna = barcoNuevo.getColumnaDeCasilla(indiceCasilla);
            if (!esCasillaValida(fila, columna)) {
                return false;
            }
            if (tableroPropio[fila][columna] != CASILLA_AGUA) {
                return false;
            }
        }

        for (int indiceCasilla = 0; indiceCasilla < longitud; indiceCasilla++) {
            int fila = barcoNuevo.getFilaDeCasilla(indiceCasilla);
            int columna = barcoNuevo.getColumnaDeCasilla(indiceCasilla);
            tableroPropio[fila][columna] = CASILLA_BARCO;
        }

        barcosPropios.add(barcoNuevo);
        return true;
    }

    public void colocarFlotaAleatoria() {
        for (int indiceBarco = 0; indiceBarco < LONGITUDES_BARCOS.length; indiceBarco++) {
            boolean barcoColocado = false;
            while (!barcoColocado) {
                int filaInicial = generadorAleatorio.nextInt(TAMANO_TABLERO);
                int columnaInicial = generadorAleatorio.nextInt(TAMANO_TABLERO);
                boolean horizontal = generadorAleatorio.nextBoolean();
                barcoColocado = colocarBarco(NOMBRES_BARCOS[indiceBarco], LONGITUDES_BARCOS[indiceBarco], filaInicial, columnaInicial, horizontal);
            }
        }
    }

    public String recibirDisparo(int fila, int columna) {
        barcoHundidoEnUltimoDisparo = null;

        if (!esCasillaValida(fila, columna)) {
            return RESULTADO_INVALIDO;
        }

        char estadoCasilla = tableroPropio[fila][columna];

        if (estadoCasilla == CASILLA_TOCADA || estadoCasilla == CASILLA_FALLO || estadoCasilla == CASILLA_HUNDIDA) {
            return RESULTADO_REPETIDO;
        }

        if (estadoCasilla == CASILLA_AGUA) {
            tableroPropio[fila][columna] = CASILLA_FALLO;
            return RESULTADO_AGUA;
        }

        Barco barcoImpactado = buscarBarcoEnCasilla(fila, columna);
        barcoImpactado.registrarImpacto(fila, columna);

        if (barcoImpactado.estaHundido()) {
            marcarBarcoComoHundido(tableroPropio, barcoImpactado);
            barcoHundidoEnUltimoDisparo = barcoImpactado;
            return RESULTADO_HUNDIDO;
        }

        tableroPropio[fila][columna] = CASILLA_TOCADA;
        return RESULTADO_TOCADO;
    }

    public void registrarResultadoDisparo(int fila, int columna, String resultado) {
        if (!esCasillaValida(fila, columna)) {
            return;
        }
        if (RESULTADO_AGUA.equals(resultado)) {
            tableroDeTiro[fila][columna] = CASILLA_FALLO;
        } else if (RESULTADO_TOCADO.equals(resultado) || RESULTADO_HUNDIDO.equals(resultado)) {
            tableroDeTiro[fila][columna] = CASILLA_TOCADA;
        }
    }

    public void registrarBarcoEnemigoHundido(Barco barcoEnemigo) {
        marcarBarcoComoHundido(tableroDeTiro, barcoEnemigo);
        barcosEnemigosHundidos.add(barcoEnemigo);
    }

    public boolean todosLosBarcosHundidos() {
        for (Barco barco : barcosPropios) {
            if (!barco.estaHundido()) {
                return false;
            }
        }
        return true;
    }

    public Barco getBarcoHundidoEnUltimoDisparo() {
        return barcoHundidoEnUltimoDisparo;
    }

    public char[][] getTableroPropio() {
        return tableroPropio;
    }

    public char[][] getTableroDeTiro() {
        return tableroDeTiro;
    }

    public List<Barco> getBarcosPropios() {
        return barcosPropios;
    }

    public char[][] copiarTableroPropio() {
        return copiarTablero(tableroPropio);
    }

    public char[][] copiarTableroDeTiro() {
        return copiarTablero(tableroDeTiro);
    }

    public List<Barco> copiarBarcosEnemigosHundidos() {
        return new ArrayList<Barco>(barcosEnemigosHundidos);
    }

    private Barco buscarBarcoEnCasilla(int fila, int columna) {
        for (Barco barco : barcosPropios) {
            if (barco.ocupaCasilla(fila, columna)) {
                return barco;
            }
        }
        return null;
    }

    private static void marcarBarcoComoHundido(char[][] tablero, Barco barco) {
        for (int indiceCasilla = 0; indiceCasilla < barco.getLongitud(); indiceCasilla++) {
            int fila = barco.getFilaDeCasilla(indiceCasilla);
            int columna = barco.getColumnaDeCasilla(indiceCasilla);
            if (esCasillaValida(fila, columna)) {
                tablero[fila][columna] = CASILLA_HUNDIDA;
            }
        }
    }

    private static char[][] copiarTablero(char[][] tableroOriginal) {
        char[][] tableroCopia = new char[TAMANO_TABLERO][TAMANO_TABLERO];
        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            for (int columna = 0; columna < TAMANO_TABLERO; columna++) {
                tableroCopia[fila][columna] = tableroOriginal[fila][columna];
            }
        }
        return tableroCopia;
    }
}