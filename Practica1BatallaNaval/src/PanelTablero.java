import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Componente visual reutilizable para UN tablero de 10x10. Se usa tres
 * veces dentro de PanelJuego: para colocar tus barcos, para "tu flota"
 * y para el "tablero enemigo".
 *
 * No conoce reglas del juego: solo recibe un char[][] con el estado
 * de cada casilla y una lista de barcos a dibujar completos, y avisa
 * los clics mediante un BiConsumer (fila, columna).
 *
 * Aquí adentro también viven:
 *  - TipoEfecto / Efecto : animación de splash y explosión
 *  - dibujarSilueta      : el dibujo de cada barco (antes DibujoBarco)
 */
public class PanelTablero extends PanelBase {

    /** Tipos de animación que PanelJuego puede pedir. */
    public enum TipoEfecto { SPLASH, EXPLOSION, EXPLOSION_GRANDE }

    private static final int TAM = LogicaBarcos.TAM;
    private static final long RADAR_DURACION_MS = 1100;

    // ---------- Efecto (animación de una casilla) ----------
    private static class Efecto {
        final TipoEfecto tipo;
        final int fila;
        final int col;
        final long inicio;
        final long duracionMs;

        Efecto(TipoEfecto tipo, int fila, int col) {
            this.tipo = tipo;
            this.fila = fila;
            this.col = col;
            this.inicio = System.currentTimeMillis();
            if (tipo == TipoEfecto.SPLASH) {
                duracionMs = 550;
            } else if (tipo == TipoEfecto.EXPLOSION) {
                duracionMs = 800;
            } else {
                duracionMs = 1300;
            }
        }

        /** 0.0 (recién creado) a 1.0 (animación terminada). */
        double progreso() {
            long transcurrido = System.currentTimeMillis() - inicio;
            return Math.min(1.0, transcurrido / (double) duracionMs);
        }

        boolean terminado() {
            return progreso() >= 1.0;
        }
    }

    private char[][] estados;
    private List<LogicaBarcos.Barco> barcosVisibles = new ArrayList<>();
    private final List<Efecto> efectos = new ArrayList<>();

    private boolean interactivo = false;
    private BiConsumer<Integer, Integer> oyente;

    private boolean radarActivo = false;
    private double anguloRadar = 0;
    private long radarElapsed = 0;
    private int radarFila, radarCol;
    private Color colorRadar = new Color(60, 220, 90);
    private Runnable radarCallback;

    private int celda = 30;
    private int margenX = 20;
    private int margenY = 20;

    private final Timer animador;

    public PanelTablero() {
        estados = new char[TAM][TAM];
        for (char[] fila : estados) Arrays.fill(fila, LogicaBarcos.AGUA);

        animador = new Timer(30, e -> tick());
        animador.start();

        // mousePressed (y no mouseClicked): mouseClicked se pierde si el
        // puntero se mueve un píxel entre presionar y soltar.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!interactivo || radarActivo || oyente == null) return;
                int x = e.getX() - margenX;
                int y = e.getY() - margenY;
                // Sin este chequeo, un clic en el margen izquierdo/superior
                // (x o y negativos de hasta -celda+1) daba 0 al dividir y se
                // tomaba como la fila/columna 0.
                if (x < 0 || y < 0) return;
                int col = x / celda;
                int fila = y / celda;
                if (fila < TAM && col < TAM) {
                    oyente.accept(fila, col);
                }
            }
        });
    }

    private void tick() {
        boolean necesitaRepintar = false;

        if (radarActivo) {
            anguloRadar += 12;
            radarElapsed += 30;
            if (radarElapsed >= RADAR_DURACION_MS) {
                radarActivo = false;
                Runnable callback = radarCallback;
                radarCallback = null;
                if (callback != null) callback.run();
            }
            necesitaRepintar = true;
        }

        if (!efectos.isEmpty()) {
            efectos.removeIf(Efecto::terminado);
            necesitaRepintar = true;
        }

        if (necesitaRepintar) repaint();
    }

    public void actualizar(char[][] estados, List<LogicaBarcos.Barco> barcosVisibles) {
        this.estados = estados;
        this.barcosVisibles = barcosVisibles;
        repaint();
    }

    public void setInteractivo(boolean valor) { this.interactivo = valor; }
    public void setOyente(BiConsumer<Integer, Integer> oyente) { this.oyente = oyente; }

    @Override
    public void detener() {
        super.detener();
        animador.stop();
    }

    public void agregarEfecto(TipoEfecto tipo, int fila, int col) {
        efectos.add(new Efecto(tipo, fila, col));
        repaint();
    }

    public void mostrarRadar(int fila, int col, Color color, Runnable alTerminar) {
        this.radarFila = fila;
        this.radarCol = col;
        this.colorRadar = color;
        this.anguloRadar = 0;
        this.radarElapsed = 0;
        this.radarActivo = true;
        this.radarCallback = alTerminar;
    }

    private void calcularGeometria() {
        int ancho = getWidth();
        int alto = getHeight();
        int disponible = Math.min(ancho, alto) - 30;
        celda = Math.max(12, disponible / TAM);
        int tableroPx = celda * TAM;
        margenX = (ancho - tableroPx) / 2;
        margenY = Math.max(24, (alto - tableroPx) / 2);
    }

    @Override
    protected void dibujarContenido(Graphics2D g2) {
        calcularGeometria();

        for (int f = 0; f < TAM; f++) {
            for (int c = 0; c < TAM; c++) {
                int x = margenX + c * celda;
                int y = margenY + f * celda;
                dibujarCelda(g2, x, y, estados[f][c]);
            }
        }

        for (LogicaBarcos.Barco b : barcosVisibles) {
            dibujarBarcoCompleto(g2, b);
        }

        dibujarEtiquetas(g2);

        for (Efecto ef : efectos) {
            int cx = margenX + ef.col * celda + celda / 2;
            int cy = margenY + ef.fila * celda + celda / 2;
            dibujarEfecto(g2, ef, cx, cy, celda);
        }

        if (radarActivo) dibujarRadar(g2);
    }

    private void dibujarCelda(Graphics2D g2, int x, int y, char estado) {
        g2.setColor(new Color(255, 255, 255, 25));
        g2.fillRect(x, y, celda, celda);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawRect(x, y, celda, celda);

        if (estado == LogicaBarcos.FALLO) {
            int r = (int) (celda * 0.12);
            g2.setColor(new Color(230, 240, 255, 190));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x + celda / 2 - r, y + celda / 2 - r, r * 2, r * 2);
        } else if (estado == LogicaBarcos.TOCADO || estado == LogicaBarcos.HUNDIDO) {
            int r = (int) (celda * 0.26);
            g2.setColor(new Color(40, 20, 10, 170));
            g2.fillOval(x + celda / 2 - r, y + celda / 2 - r, r * 2, r * 2);
            g2.setColor(new Color(255, 100, 30, 210));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(x + celda / 2 - r, y + celda / 2 - r, x + celda / 2 + r, y + celda / 2 + r);
            g2.drawLine(x + celda / 2 - r, y + celda / 2 + r, x + celda / 2 + r, y + celda / 2 - r);
        }
    }

    private void dibujarBarcoCompleto(Graphics2D g2, LogicaBarcos.Barco b) {
        List<int[]> pos = b.getPosiciones();
        if (pos.isEmpty()) return;
        int[] inicio = pos.get(0);
        boolean horizontal = pos.size() == 1 || pos.get(0)[0] == pos.get(pos.size() - 1)[0];
        int x = margenX + inicio[1] * celda;
        int y = margenY + inicio[0] * celda;
        dibujarSilueta(g2, x, y, celda, b.getLongitud(), horizontal);
    }

    private void dibujarEtiquetas(Graphics2D g2) {
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, celda / 3)));
        for (int c = 0; c < TAM; c++) {
            String txt = String.valueOf(c + 1);
            g2.drawString(txt, margenX + c * celda + celda / 2 - 4, margenY - 8);
        }
        for (int f = 0; f < TAM; f++) {
            String txt = String.valueOf((char) ('A' + f));
            g2.drawString(txt, margenX - 18, margenY + f * celda + celda / 2 + 5);
        }
    }

    // ---------- Silueta del barco (antes la clase DibujoBarco) ----------

    /**
     * Dibuja un barco "estilizado" usando solo formas geométricas y
     * degradados de Graphics2D (sin imágenes). Siempre se dibuja
     * "acostado" en coordenadas locales; si el barco es vertical se
     * rota el lienzo 90 grados antes de dibujar.
     */
    private static void dibujarSilueta(Graphics2D g2Original, int xCelda, int yCelda, int celda, int longitud, boolean horizontal) {
        Graphics2D g2 = (Graphics2D) g2Original.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.translate(xCelda, yCelda);
        if (!horizontal) {
            g2.rotate(Math.PI / 2);
            g2.translate(0, -celda);
        }

        int anchoTotal = longitud * celda;
        double topY = celda * 0.16;
        double botY = celda * 0.84;
        double midY = celda * 0.5;

        GeneralPath casco = new GeneralPath();
        casco.moveTo(anchoTotal * 0.04, topY);
        casco.lineTo(anchoTotal * 0.82, topY);
        casco.lineTo(anchoTotal * 0.98, midY);
        casco.lineTo(anchoTotal * 0.82, botY);
        casco.lineTo(anchoTotal * 0.04, botY);
        casco.lineTo(0, midY);
        casco.closePath();

        g2.setPaint(new GradientPaint(0, (float) topY, new Color(205, 208, 212),
                0, (float) botY, new Color(85, 90, 98)));
        g2.fill(casco);

        g2.setColor(new Color(35, 38, 45));
        g2.setStroke(new BasicStroke(Math.max(1f, (float) (celda * 0.03))));
        g2.draw(casco);

        // linea de cubierta
        g2.setColor(new Color(255, 255, 255, 90));
        g2.setStroke(new BasicStroke(Math.max(1f, (float) (celda * 0.015))));
        g2.drawLine((int) (anchoTotal * 0.06), (int) (topY + celda * 0.05),
                (int) (anchoTotal * 0.8), (int) (topY + celda * 0.05));

        // superestructura: 1 bloque en barcos chicos, 2 en barcos grandes
        int bloques = longitud >= 4 ? 2 : 1;
        double anchoBloque = anchoTotal * (longitud >= 4 ? 0.18 : 0.28);
        double altoBloque = celda * 0.36;
        for (int b = 0; b < bloques; b++) {
            double cx = anchoTotal * (bloques == 1 ? 0.5 : (0.35 + b * 0.3));
            double bx = cx - anchoBloque / 2;
            double by = midY - altoBloque / 2;
            RoundRectangle2D bloque = new RoundRectangle2D.Double(bx, by, anchoBloque, altoBloque, celda * 0.08, celda * 0.08);
            g2.setPaint(new GradientPaint(0, (float) by, new Color(235, 236, 238),
                    0, (float) (by + altoBloque), new Color(150, 153, 158)));
            g2.fill(bloque);
            g2.setColor(new Color(35, 38, 45));
            g2.setStroke(new BasicStroke(Math.max(1f, (float) (celda * 0.02))));
            g2.draw(bloque);
        }

        // mastil con antena de radar
        double mastilX = anchoTotal * (bloques == 1 ? 0.5 : 0.65);
        g2.setColor(new Color(60, 62, 68));
        g2.setStroke(new BasicStroke(Math.max(1f, (float) (celda * 0.02))));
        g2.drawLine((int) mastilX, (int) (midY - altoBloque / 2), (int) mastilX, (int) (topY - celda * 0.02));
        int radioAntena = (int) (celda * 0.05);
        g2.fillOval((int) (mastilX - radioAntena), (int) (topY - celda * 0.02) - radioAntena, radioAntena * 2, radioAntena * 2);

        // pequeños cañones para barcos medianos/grandes
        if (longitud >= 3) {
            double[] posiciones = longitud >= 4 ? new double[]{0.12, 0.9} : new double[]{0.12};
            for (double pfrac : posiciones) {
                double gx = anchoTotal * pfrac;
                double gAncho = celda * 0.12;
                double gAlto = celda * 0.22;
                g2.setColor(new Color(50, 52, 58));
                g2.fillRoundRect((int) (gx - gAncho / 2), (int) (midY - gAlto / 2), (int) gAncho, (int) gAlto, 3, 3);
            }
        }

        // luz de navegación en la proa
        g2.setColor(new Color(220, 40, 40));
        int rLuz = (int) (celda * 0.08);
        g2.fillOval((int) (anchoTotal * 0.93) - rLuz / 2, (int) (midY - rLuz / 2), rLuz, rLuz);

        g2.dispose();
    }

    // ---------- Efectos (splash / explosión) ----------

    private void dibujarEfecto(Graphics2D g2, Efecto ef, int cx, int cy, int celda) {
        double p = ef.progreso();
        if (ef.tipo == TipoEfecto.SPLASH) {
            dibujarSplash(g2, cx, cy, celda, p);
        } else if (ef.tipo == TipoEfecto.EXPLOSION) {
            dibujarExplosion(g2, cx, cy, celda, p, 1.0);
        } else {
            dibujarExplosion(g2, cx, cy, celda, p, 1.8);
        }
    }

    private void dibujarSplash(Graphics2D g2, int cx, int cy, int celda, double p) {
        int radioMax = (int) (celda * 0.55);
        int radio = (int) (radioMax * p);
        int alfa = Math.max(0, (int) (200 * (1 - p)));

        g2.setColor(new Color(220, 240, 255, alfa));
        g2.fillOval(cx - radio, cy - radio, radio * 2, radio * 2);
        g2.setColor(new Color(255, 255, 255, alfa));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - radio, cy - radio, radio * 2, radio * 2);

        int gotas = 5;
        for (int i = 0; i < gotas; i++) {
            double angulo = (2 * Math.PI / gotas) * i;
            int dist = (int) (radioMax * 0.9 * p);
            int gx = cx + (int) (Math.cos(angulo) * dist);
            int gy = cy + (int) (Math.sin(angulo) * dist);
            int r = (int) (celda * 0.05 * (1 - p)) + 1;
            g2.setColor(new Color(200, 230, 255, alfa));
            g2.fillOval(gx - r, gy - r, r * 2, r * 2);
        }
    }

    private void dibujarExplosion(Graphics2D g2, int cx, int cy, int celda, double p, double escala) {
        int radioMax = (int) (celda * 0.7 * escala);
        int radio = Math.max(1, (int) (radioMax * Math.min(1.0, p * 1.6)));
        int alfaCuerpo = Math.max(0, (int) (220 * (1 - p)));

        if (p < 0.25) {
            int alfaFlash = (int) (255 * (1 - p / 0.25));
            g2.setColor(new Color(255, 255, 255, alfaFlash));
            int rf = (int) (radioMax * 1.1);
            g2.fillOval(cx - rf, cy - rf, rf * 2, rf * 2);
        }

        RadialGradientPaint fuego = new RadialGradientPaint(
                new Point(cx, cy), radio,
                new float[]{0f, 0.6f, 1f},
                new Color[]{
                        new Color(255, 240, 180, alfaCuerpo),
                        new Color(255, 120, 30, alfaCuerpo),
                        new Color(120, 20, 10, 0)
                });
        g2.setPaint(fuego);
        g2.fillOval(cx - radio, cy - radio, radio * 2, radio * 2);

        int particulas = (int) (8 * escala);
        for (int i = 0; i < particulas; i++) {
            double angulo = (2 * Math.PI / particulas) * i + p * 2;
            int dist = (int) (radioMax * 1.3 * p);
            int px = cx + (int) (Math.cos(angulo) * dist);
            int py = cy + (int) (Math.sin(angulo) * dist);
            int r = (int) (celda * 0.06 * escala * (1 - p)) + 1;
            g2.setColor(new Color(255, 180, 60, alfaCuerpo));
            g2.fillOval(px - r, py - r, r * 2, r * 2);
        }

        if (p > 0.5) {
            int alfaHumo = Math.max(0, (int) (140 * ((p - 0.5) / 0.5) * (1 - p)));
            g2.setColor(new Color(70, 70, 70, alfaHumo));
            int rh = (int) (radioMax * 0.6);
            int subida = (int) (celda * 0.4 * (p - 0.5));
            g2.fillOval(cx - rh, cy - rh - subida, rh * 2, rh * 2);
        }
    }

    // ---------- Radar (pantalla de "apuntando") ----------

    private void dibujarRadar(Graphics2D g2) {
        int tableroPx = celda * TAM;
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(margenX, margenY, tableroPx, tableroPx);

        int cx = margenX + tableroPx / 2;
        int cy = margenY + tableroPx / 2;
        int radioMax = tableroPx / 2;

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(colorRadar.getRed(), colorRadar.getGreen(), colorRadar.getBlue(), 90));
        for (int anillo = 1; anillo <= 4; anillo++) {
            int r = radioMax * anillo / 4;
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        }
        g2.drawLine(cx - radioMax, cy, cx + radioMax, cy);
        g2.drawLine(cx, cy - radioMax, cx, cy + radioMax);

        Arc2D.Double sector = new Arc2D.Double(cx - radioMax, cy - radioMax, radioMax * 2, radioMax * 2,
                anguloRadar, 40, Arc2D.PIE);
        g2.setColor(new Color(colorRadar.getRed(), colorRadar.getGreen(), colorRadar.getBlue(), 90));
        g2.fill(sector);

        g2.setColor(colorRadar);
        g2.setStroke(new BasicStroke(2f));
        double radAngulo = Math.toRadians(-anguloRadar);
        int lx = cx + (int) (Math.cos(radAngulo) * radioMax);
        int ly = cy + (int) (Math.sin(radAngulo) * radioMax);
        g2.drawLine(cx, cy, lx, ly);

        int px = margenX + radarCol * celda + celda / 2;
        int py = margenY + radarFila * celda + celda / 2;
        boolean parpadeo = (radarElapsed / 150) % 2 == 0;
        if (parpadeo) {
            g2.setColor(colorRadar);
            int r = celda / 6;
            g2.fillOval(px - r, py - r, r * 2, r * 2);
        }

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, Math.max(12, celda / 2)));
        String texto = "ESCANEANDO...";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(texto, cx - fm.stringWidth(texto) / 2, margenY + tableroPx + 20);
    }
}
