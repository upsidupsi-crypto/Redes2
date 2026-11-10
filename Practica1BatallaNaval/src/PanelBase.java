import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

public abstract class PanelBase extends StackPane {

    private static final int CANTIDAD_DE_OLAS = 5;

    private double faseDeLasOlas = 0;
    private final Canvas canvas = new Canvas();
    private final AnimationTimer temporizadorDelAgua;

    public PanelBase() {
        getChildren().add(canvas);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        widthProperty().addListener((obs, viejoVal, nuevoVal) -> dibujar());
        heightProperty().addListener((obs, viejoVal, nuevoVal) -> dibujar());

        temporizadorDelAgua = new AnimationTimer() {
            private long ultimoCambio = 0;

            @Override
            public void handle(long horaActual) {
                // Actualizar aproximadamente cada 40 milisegundos
                if (horaActual - ultimoCambio >= 40_000_000L) {
                    faseDeLasOlas += 0.05;
                    dibujar();
                    ultimoCambio = horaActual;
                }
            }
        };
        temporizadorDelAgua.start();
    }

    public void detener() {
        temporizadorDelAgua.stop();
    }

    private void dibujar() {
        double ancho = getWidth();
        double alto = getHeight();

        if (ancho <= 0 || alto <= 0) {
            return;
        }

        GraphicsContext graficos = canvas.getGraphicsContext2D();
        graficos.clearRect(0, 0, ancho, alto);

        dibujarAgua(graficos, ancho, alto);
        dibujarContenido(graficos);
    }

    private void dibujarAgua(GraphicsContext graficos, double ancho, double alto) {

        LinearGradient degradadoDeFondo = new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#092d4f")),
                new Stop(1, Color.web("#0f5c82"))
        );
        graficos.setFill(degradadoDeFondo);
        graficos.fillRect(0, 0, ancho, alto);

        for (int numeroDeOla = 0; numeroDeOla < CANTIDAD_DE_OLAS; numeroDeOla++) {
            double alturaBase = alto * (0.15 + numeroDeOla * 0.18);
            double amplitudDeOla = 6 + numeroDeOla * 2;
            double velocidadDeOla = 0.6 + numeroDeOla * 0.15;
            double opacidad = (25 + numeroDeOla * 8) / 255.0;

            graficos.beginPath();
            graficos.moveTo(0, alturaBase);
            for (double posicionX = 0; posicionX <= ancho; posicionX += 10) {
                double posicionY = alturaBase + Math.sin((posicionX * 0.02) + faseDeLasOlas * velocidadDeOla) * amplitudDeOla;
                graficos.lineTo(posicionX, posicionY);
            }
            graficos.lineTo(ancho, alto);
            graficos.lineTo(0, alto);
            graficos.closePath();

            graficos.setFill(Color.rgb(255, 255, 255, opacidad));
            graficos.fill();
        }
    }

    protected void dibujarContenido(GraphicsContext graficos) {
    }
}