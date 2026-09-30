import java.io.Serializable;

/**
 * Un solo tipo de mensaje "genérico" para no tener que crear una clase
 * distinta por cada mensaje del protocolo. El campo "tipo" indica qué
 * significa el mensaje, y los demás campos se llenan según el tipo:
 *
 *   NOMBRE     -> texto = nombre del jugador
 *   INICIO     -> (sin datos extra)
 *   LISTO      -> (sin datos extra)
 *   TURNO      -> texto = "CLIENTE" o "SERVIDOR"
 *   DISPARO    -> fila, col
 *   RESULTADO  -> texto = "AGUA" | "TOCADO" | "HUNDIDO" | "REPETIDO", fin = true/false
 */
public class Mensaje implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tipo;
    private String texto;
    private int fila;
    private int col;
    private boolean fin;

    public Mensaje() { }

    public Mensaje(String tipo) {
        this.tipo = tipo;
    }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public int getFila() { return fila; }
    public void setFila(int fila) { this.fila = fila; }

    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }

    public boolean isFin() { return fin; }
    public void setFin(boolean fin) { this.fin = fin; }

    @Override
    public String toString() {
        return "Mensaje{tipo=" + tipo + ", texto=" + texto + ", fila=" + fila + ", col=" + col + ", fin=" + fin + "}";
    }
}
