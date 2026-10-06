import java.io.Serializable;

public class Mensaje implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TIPO_NOMBRE = "NOMBRE";
    public static final String TIPO_INICIO = "INICIO";
    public static final String TIPO_LISTO = "LISTO";
    public static final String TIPO_TURNO = "TURNO";
    public static final String TIPO_DISPARO = "DISPARO";
    public static final String TIPO_RESULTADO = "RESULTADO";
    public static final String TIPO_OCUPADO = "OCUPADO";

    public static final String TURNO_CLIENTE = "CLIENTE";
    public static final String TURNO_SERVIDOR = "SERVIDOR";

    private String tipo;
    private String texto;
    private int fila;
    private int columna;
    private boolean finDeJuego;
    private String nombreBarcoHundido;
    private int longitudBarcoHundido;
    private int filaInicialBarcoHundido;
    private int columnaInicialBarcoHundido;
    private boolean barcoHundidoHorizontal;

    public Mensaje(String tipo) {
        this.tipo = tipo;
    }

    public void agregarBarcoHundido(LogicaBarcos.Barco barcoHundido) {
        nombreBarcoHundido = barcoHundido.getNombre();
        longitudBarcoHundido = barcoHundido.getLongitud();
        filaInicialBarcoHundido = barcoHundido.getFilaInicial();
        columnaInicialBarcoHundido = barcoHundido.getColumnaInicial();
        barcoHundidoHorizontal = barcoHundido.isHorizontal();
    }

    public boolean tieneBarcoHundido() {
        return nombreBarcoHundido != null;
    }

    public LogicaBarcos.Barco obtenerBarcoHundido() {
        return new LogicaBarcos.Barco(nombreBarcoHundido, longitudBarcoHundido,
                filaInicialBarcoHundido, columnaInicialBarcoHundido, barcoHundidoHorizontal);
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public int getFila() {
        return fila;
    }

    public void setFila(int fila) {
        this.fila = fila;
    }

    public int getColumna() {
        return columna;
    }

    public void setColumna(int columna) {
        this.columna = columna;
    }

    public boolean isFinDeJuego() {
        return finDeJuego;
    }

    public void setFinDeJuego(boolean finDeJuego) {
        this.finDeJuego = finDeJuego;
    }

    public String getNombreBarcoHundido() {
        return nombreBarcoHundido;
    }
}