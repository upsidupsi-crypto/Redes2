import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * Panel base que dibuja un fondo de agua animada (varias "olas" que
 * se desplazan con el tiempo). Las pantallas del juego (menú, juego y
 * tablero) heredan de esta clase y solo tienen que implementar
 * dibujarContenido(g) para agregar lo suyo encima del agua.
 *
 * La animación se logra con un javax.swing.Timer que cada ~40ms
 * avanza una variable "fase" y pide repintar el panel.
 */
public abstract class PanelBase extends JPanel {

    private double fase = 0;
    private final Timer temporizadorAgua;

    public PanelBase() {
        setDoubleBuffered(true);
        temporizadorAgua = new Timer(40, e -> {
            fase += 0.05;
            repaint();
        });
        temporizadorAgua.start();
    }

    /** Detiene la animación del agua. Llamar cuando el panel ya no se use más. */
    public void detener() {
        temporizadorAgua.stop();
    }

    protected abstract void dibujarContenido(Graphics2D g);

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        dibujarAgua(g2);
        dibujarContenido(g2);
    }

    private void dibujarAgua(Graphics2D g2) {
        int ancho = getWidth();
        int alto = getHeight();

        GradientPaint fondo = new GradientPaint(0, 0, new Color(9, 45, 79),
                0, alto, new Color(15, 92, 130));
        g2.setPaint(fondo);
        g2.fillRect(0, 0, ancho, alto);

        // varias bandas de "olas" translúcidas, cada una con su propia velocidad
        int franjas = 5;
        for (int f = 0; f < franjas; f++) {
            double alturaBase = alto * (0.15 + f * 0.18);
            double amplitud = 6 + f * 2;
            double velocidad = 0.6 + f * 0.15;
            int alfa = 25 + f * 8;

            GeneralPath ola = new GeneralPath();
            ola.moveTo(0, alturaBase);
            for (int x = 0; x <= ancho; x += 10) {
                double y = alturaBase + Math.sin((x * 0.02) + fase * velocidad) * amplitud;
                ola.lineTo(x, y);
            }
            ola.lineTo(ancho, alto);
            ola.lineTo(0, alto);
            ola.closePath();

            g2.setColor(new Color(255, 255, 255, alfa));
            g2.fill(ola);
        }
    }

    protected double getFase() { return fase; }
}
