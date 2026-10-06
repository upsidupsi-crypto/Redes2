import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.GeneralPath;

public abstract class PanelBase extends JPanel {

    private static final int MILISEGUNDOS_ENTRE_CUADROS = 40;
    private static final int CANTIDAD_DE_OLAS = 5;

    private double faseDeLasOlas = 0;
    private final Timer temporizadorDelAgua;

    public PanelBase() {
        setDoubleBuffered(true);
        temporizadorDelAgua = new Timer(MILISEGUNDOS_ENTRE_CUADROS, new ActionListener() {
            public void actionPerformed(ActionEvent evento) {
                faseDeLasOlas = faseDeLasOlas + 0.05;
                repaint();
            }
        });
        temporizadorDelAgua.start();
    }

    public void detener() {
        temporizadorDelAgua.stop();
    }

    protected abstract void dibujarContenido(Graphics2D graficos);

    protected void paintComponent(Graphics graficosBase) {
        super.paintComponent(graficosBase);
        Graphics2D graficos = (Graphics2D) graficosBase;
        graficos.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        dibujarAgua(graficos);
        dibujarContenido(graficos);
    }

    private void dibujarAgua(Graphics2D graficos) {
        int anchoDelPanel = getWidth();
        int altoDelPanel = getHeight();

        GradientPaint degradadoDeFondo = new GradientPaint(0, 0, new Color(9, 45, 79),
                0, altoDelPanel, new Color(15, 92, 130));
        graficos.setPaint(degradadoDeFondo);
        graficos.fillRect(0, 0, anchoDelPanel, altoDelPanel);

        for (int numeroDeOla = 0; numeroDeOla < CANTIDAD_DE_OLAS; numeroDeOla++) {
            double alturaBase = altoDelPanel * (0.15 + numeroDeOla * 0.18);
            double amplitudDeOla = 6 + numeroDeOla * 2;
            double velocidadDeOla = 0.6 + numeroDeOla * 0.15;
            int transparencia = 25 + numeroDeOla * 8;

            GeneralPath formaDeOla = new GeneralPath();
            formaDeOla.moveTo(0, alturaBase);
            for (int posicionX = 0; posicionX <= anchoDelPanel; posicionX += 10) {
                double posicionY = alturaBase + Math.sin((posicionX * 0.02) + faseDeLasOlas * velocidadDeOla) * amplitudDeOla;
                formaDeOla.lineTo(posicionX, posicionY);
            }
            formaDeOla.lineTo(anchoDelPanel, altoDelPanel);
            formaDeOla.lineTo(0, altoDelPanel);
            formaDeOla.closePath();

            graficos.setColor(new Color(255, 255, 255, transparencia));
            graficos.fill(formaDeOla);
        }
    }
}