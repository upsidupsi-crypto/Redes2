import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class PanelTablero extends PanelBase {

    public static final int EFECTO_SPLASH = 0;
    public static final int EFECTO_EXPLOSION = 1;
    public static final int EFECTO_EXPLOSION_GRANDE = 2;

    private static final int TAMANO_TABLERO = LogicaBarcos.TAMANO_TABLERO;
    private static final int DURACION_RADAR_MILISEGUNDOS = 1100;

    public interface OyenteDeCasilla {
        void alSeleccionarCasilla(int fila, int columna);
    }

    private static class Efecto {
        private final int tipoDeEfecto;
        private final int fila;
        private final int columna;
        private final long momentoDeInicio;
        private final long duracionEnMilisegundos;

        Efecto(int tipoDeEfecto, int fila, int columna) {
            this.tipoDeEfecto = tipoDeEfecto;
            this.fila = fila;
            this.columna = columna;
            this.momentoDeInicio = System.currentTimeMillis();
            if (tipoDeEfecto == EFECTO_SPLASH) {
                this.duracionEnMilisegundos = 550;
            } else if (tipoDeEfecto == EFECTO_EXPLOSION) {
                this.duracionEnMilisegundos = 800;
            } else {
                this.duracionEnMilisegundos = 1300;
            }
        }

        double calcularProgreso() {
            long tiempoTranscurrido = System.currentTimeMillis() - momentoDeInicio;
            return Math.min(1.0, tiempoTranscurrido / (double) duracionEnMilisegundos);
        }

        boolean haTerminado() {
            return calcularProgreso() >= 1.0;
        }
    }

    private char[][] estadosDeCasillas;
    private List<LogicaBarcos.Barco> barcosVisibles;
    private final List<Efecto> efectosActivos;

    private boolean interactivo;
    private OyenteDeCasilla oyenteDeCasilla;

    private boolean radarActivo;
    private double anguloDelRadar;
    private long tiempoTranscurridoDelRadar;
    private long ultimoTiempoRadarMs;
    private int filaDelRadar;
    private int columnaDelRadar;
    private Color colorDelRadar;
    private Runnable accionAlTerminarElRadar;

    private double tamanoCelda;
    private double margenIzquierdo;
    private double margenSuperior;

    public PanelTablero() {
        estadosDeCasillas = new char[TAMANO_TABLERO][TAMANO_TABLERO];
        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            Arrays.fill(estadosDeCasillas[fila], LogicaBarcos.CASILLA_AGUA);
        }
        barcosVisibles = new ArrayList<>();
        efectosActivos = new ArrayList<>();

        interactivo = false;
        oyenteDeCasilla = null;

        radarActivo = false;
        anguloDelRadar = 0;
        tiempoTranscurridoDelRadar = 0;
        filaDelRadar = 0;
        columnaDelRadar = 0;
        colorDelRadar = Color.rgb(60, 220, 90);
        accionAlTerminarElRadar = null;

        tamanoCelda = 30;
        margenIzquierdo = 20;
        margenSuperior = 20;

        // Escuchador de clics del ratón en JavaFX
        setOnMousePressed(this::alHacerClic);
    }

    private void alHacerClic(MouseEvent evento) {
        if (!interactivo || radarActivo || oyenteDeCasilla == null) {
            return;
        }
        double posicionXEnTablero = evento.getX() - margenIzquierdo;
        double posicionYEnTablero = evento.getY() - margenSuperior;
        if (posicionXEnTablero < 0 || posicionYEnTablero < 0) {
            return;
        }
        int columna = (int) (posicionXEnTablero / tamanoCelda);
        int fila = (int) (posicionYEnTablero / tamanoCelda);
        if (fila >= 0 && fila < TAMANO_TABLERO && columna >= 0 && columna < TAMANO_TABLERO) {
            oyenteDeCasilla.alSeleccionarCasilla(fila, columna);
        }
    }

    public void actualizar(char[][] nuevosEstados, List<LogicaBarcos.Barco> nuevosBarcosVisibles) {
        this.estadosDeCasillas = nuevosEstados;
        this.barcosVisibles = nuevosBarcosVisibles;
    }

    public char getEstadoDeCasilla(int fila, int columna) {
        return estadosDeCasillas[fila][columna];
    }

    public void setInteractivo(boolean valor) {
        this.interactivo = valor;
    }

    public void setOyenteDeCasilla(OyenteDeCasilla oyente) {
        this.oyenteDeCasilla = oyente;
    }

    public void agregarEfecto(int tipoDeEfecto, int fila, int columna) {
        efectosActivos.add(new Efecto(tipoDeEfecto, fila, columna));
    }

    public void mostrarRadar(int fila, int columna, Color color, Runnable accionAlTerminar) {
        this.filaDelRadar = fila;
        this.columnaDelRadar = columna;
        this.colorDelRadar = color;
        this.anguloDelRadar = 0;
        this.tiempoTranscurridoDelRadar = 0;
        this.ultimoTiempoRadarMs = System.currentTimeMillis();
        this.accionAlTerminarElRadar = accionAlTerminar;
        this.radarActivo = true;
    }

    private void calcularGeometria() {
        double anchoDelPanel = getWidth();
        double altoDelPanel = getHeight();
        double espacioDisponible = Math.min(anchoDelPanel, altoDelPanel) - 30;
        tamanoCelda = Math.max(12, espacioDisponible / TAMANO_TABLERO);
        double tamanoDelTableroEnPixeles = tamanoCelda * TAMANO_TABLERO;
        margenIzquierdo = (anchoDelPanel - tamanoDelTableroEnPixeles) / 2;
        margenSuperior = Math.max(24, (altoDelPanel - tamanoDelTableroEnPixeles) / 2);
    }

    @Override
    protected void dibujarContenido(GraphicsContext graficos) {
        calcularGeometria();

        // Actualizar temporizador del radar si está activo
        if (radarActivo) {
            long ahora = System.currentTimeMillis();
            long delta = ahora - ultimoTiempoRadarMs;
            ultimoTiempoRadarMs = ahora;

            anguloDelRadar += (delta * 0.3); // Velocidad de rotación
            tiempoTranscurridoDelRadar += delta;

            if (tiempoTranscurridoDelRadar >= DURACION_RADAR_MILISEGUNDOS) {
                radarActivo = false;
                Runnable accionPendiente = accionAlTerminarElRadar;
                accionAlTerminarElRadar = null;
                if (accionPendiente != null) {
                    accionPendiente.run();
                }
            }
        }

        // Limpiar efectos finalizados
        if (!efectosActivos.isEmpty()) {
            Iterator<Efecto> iterador = efectosActivos.iterator();
            while (iterador.hasNext()) {
                if (iterador.next().haTerminado()) {
                    iterador.remove();
                }
            }
        }

        // 1. Dibujar casillas del tablero
        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            for (int columna = 0; columna < TAMANO_TABLERO; columna++) {
                double posicionX = margenIzquierdo + columna * tamanoCelda;
                double posicionY = margenSuperior + fila * tamanoCelda;
                dibujarCasilla(graficos, posicionX, posicionY, estadosDeCasillas[fila][columna]);
            }
        }

        // 2. Dibujar barcos visibles
        for (LogicaBarcos.Barco barco : barcosVisibles) {
            dibujarBarcoCompleto(graficos, barco);
        }

        // 3. Dibujar coordenadas A-J, 1-10
        dibujarEtiquetasDeCoordenadas(graficos);

        // 4. Dibujar efectos activos (explosiones/salpicaduras)
        for (Efecto efecto : efectosActivos) {
            double centroX = margenIzquierdo + efecto.columna * tamanoCelda + tamanoCelda / 2;
            double centroY = margenSuperior + efecto.fila * tamanoCelda + tamanoCelda / 2;
            dibujarEfecto(graficos, efecto, centroX, centroY);
        }

        // 5. Dibujar animación de radar si está activo
        if (radarActivo) {
            dibujarRadar(graficos);
        }
    }

    private void dibujarCasilla(GraphicsContext graficos, double posicionX, double posicionY, char estadoDeCasilla) {
        graficos.setFill(Color.rgb(255, 255, 255, 0.10));
        graficos.fillRect(posicionX, posicionY, tamanoCelda, tamanoCelda);
        graficos.setStroke(Color.rgb(255, 255, 255, 0.24));
        graficos.setLineWidth(1.0);
        graficos.strokeRect(posicionX, posicionY, tamanoCelda, tamanoCelda);

        double centroX = posicionX + tamanoCelda / 2;
        double centroY = posicionY + tamanoCelda / 2;

        if (estadoDeCasilla == LogicaBarcos.CASILLA_FALLO) {
            double radio = tamanoCelda * 0.12;
            graficos.setStroke(Color.rgb(230, 240, 255, 0.75));
            graficos.setLineWidth(2.0);
            graficos.strokeOval(centroX - radio, centroY - radio, radio * 2, radio * 2);
        } else if (estadoDeCasilla == LogicaBarcos.CASILLA_TOCADA || estadoDeCasilla == LogicaBarcos.CASILLA_HUNDIDA) {
            double radio = tamanoCelda * 0.26;
            graficos.setFill(Color.rgb(40, 20, 10, 0.67));
            graficos.fillOval(centroX - radio, centroY - radio, radio * 2, radio * 2);
            graficos.setStroke(Color.rgb(255, 100, 30, 0.82));
            graficos.setLineWidth(2.0);
            graficos.strokeLine(centroX - radio, centroY - radio, centroX + radio, centroY + radio);
            graficos.strokeLine(centroX - radio, centroY + radio, centroX + radio, centroY - radio);
        }
    }

    private void dibujarBarcoCompleto(GraphicsContext graficos, LogicaBarcos.Barco barco) {
        double posicionX = margenIzquierdo + barco.getColumnaInicial() * tamanoCelda;
        double posicionY = margenSuperior + barco.getFilaInicial() * tamanoCelda;
        dibujarSilueta(graficos, posicionX, posicionY, tamanoCelda, barco.getLongitud(), barco.isHorizontal());
    }

    private void dibujarEtiquetasDeCoordenadas(GraphicsContext graficos) {
        graficos.setFill(Color.rgb(255, 255, 255, 0.82));
        graficos.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.max(10, tamanoCelda / 3)));
        graficos.setTextAlign(TextAlignment.CENTER);
        graficos.setTextBaseline(VPos.CENTER);

        for (int columna = 0; columna < TAMANO_TABLERO; columna++) {
            String textoCol = String.valueOf(columna + 1);
            graficos.fillText(textoCol, margenIzquierdo + columna * tamanoCelda + tamanoCelda / 2, margenSuperior - 10);
        }
        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            String textoFila = String.valueOf((char) ('A' + fila));
            graficos.fillText(textoFila, margenIzquierdo - 14, margenSuperior + fila * tamanoCelda + tamanoCelda / 2);
        }
    }

    private static void dibujarSilueta(GraphicsContext graficos, double posicionXDeCelda, double posicionYDeCelda,
                                       double tamanoDeCelda, int longitudDelBarco, boolean horizontal) {
        graficos.save();

        graficos.translate(posicionXDeCelda, posicionYDeCelda);
        if (!horizontal) {
            graficos.rotate(90);
            graficos.translate(0, -tamanoDeCelda);
        }

        double anchoTotal = longitudDelBarco * tamanoDeCelda;
        double bordeSuperior = tamanoDeCelda * 0.16;
        double bordeInferior = tamanoDeCelda * 0.84;
        double lineaMedia = tamanoDeCelda * 0.5;

        // Casco del barco
        graficos.beginPath();
        graficos.moveTo(anchoTotal * 0.04, bordeSuperior);
        graficos.lineTo(anchoTotal * 0.82, bordeSuperior);
        graficos.lineTo(anchoTotal * 0.98, lineaMedia);
        graficos.lineTo(anchoTotal * 0.82, bordeInferior);
        graficos.lineTo(anchoTotal * 0.04, bordeInferior);
        graficos.lineTo(0, lineaMedia);
        graficos.closePath();

        LinearGradient degradadoCasco = new LinearGradient(
                0, bordeSuperior, 0, bordeInferior, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#cdd0d4")),
                new Stop(1, Color.web("#555a62"))
        );
        graficos.setFill(degradadoCasco);
        graficos.fill();

        graficos.setStroke(Color.web("#23262d"));
        graficos.setLineWidth(Math.max(1.0, tamanoDeCelda * 0.03));
        graficos.stroke();

        // Línea metálica de brillo
        graficos.setStroke(Color.rgb(255, 255, 255, 0.35));
        graficos.setLineWidth(Math.max(1.0, tamanoDeCelda * 0.015));
        graficos.strokeLine(anchoTotal * 0.06, bordeSuperior + tamanoDeCelda * 0.05,
                anchoTotal * 0.8, bordeSuperior + tamanoDeCelda * 0.05);

        // Bloques de superestructura
        int cantidadDeBloques = (longitudDelBarco >= 4) ? 2 : 1;
        double anchoDeBloque = (cantidadDeBloques == 2) ? anchoTotal * 0.18 : anchoTotal * 0.28;
        double altoDeBloque = tamanoDeCelda * 0.36;

        for (int numeroDeBloque = 0; numeroDeBloque < cantidadDeBloques; numeroDeBloque++) {
            double centroDelBloque = (cantidadDeBloques == 2)
                    ? anchoTotal * (0.35 + numeroDeBloque * 0.3)
                    : anchoTotal * 0.5;

            double posXBloque = centroDelBloque - anchoDeBloque / 2;
            double posYBloque = lineaMedia - altoDeBloque / 2;
            double radioArc = tamanoDeCelda * 0.08;

            LinearGradient degradadoBloque = new LinearGradient(
                    0, posYBloque, 0, posYBloque + altoDeBloque, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#ebecee")),
                    new Stop(1, Color.web("#96999e"))
            );
            graficos.setFill(degradadoBloque);
            graficos.fillRoundRect(posXBloque, posYBloque, anchoDeBloque, altoDeBloque, radioArc, radioArc);

            graficos.setStroke(Color.web("#23262d"));
            graficos.setLineWidth(Math.max(1.0, tamanoDeCelda * 0.02));
            graficos.strokeRoundRect(posXBloque, posYBloque, anchoDeBloque, altoDeBloque, radioArc, radioArc);
        }

        // Mástil y antena
        double posicionDelMastil = (cantidadDeBloques == 2) ? anchoTotal * 0.65 : anchoTotal * 0.5;
        graficos.setStroke(Color.web("#3c3e44"));
        graficos.setLineWidth(Math.max(1.0, tamanoDeCelda * 0.02));
        graficos.strokeLine(posicionDelMastil, lineaMedia - altoDeBloque / 2,
                posicionDelMastil, bordeSuperior - tamanoDeCelda * 0.02);

        double radioAntena = tamanoDeCelda * 0.05;
        graficos.setFill(Color.web("#3c3e44"));
        graficos.fillOval(posicionDelMastil - radioAntena, (bordeSuperior - tamanoDeCelda * 0.02) - radioAntena,
                radioAntena * 2, radioAntena * 2);

        // Cañones
        if (longitudDelBarco >= 3) {
            double[] posicionesDeCanones = (longitudDelBarco >= 4) ? new double[]{0.12, 0.9} : new double[]{0.12};
            for (double fraccion : posicionesDeCanones) {
                double posCanon = anchoTotal * fraccion;
                double anchoCanon = tamanoDeCelda * 0.12;
                double altoCanon = tamanoDeCelda * 0.22;
                graficos.setFill(Color.web("#32343a"));
                graficos.fillRoundRect(posCanon - anchoCanon / 2, lineaMedia - altoCanon / 2, anchoCanon, altoCanon, 3, 3);
            }
        }

        // Luz de navegación roja
        graficos.setFill(Color.web("#dc2828"));
        double radioLuz = tamanoDeCelda * 0.08;
        graficos.fillOval(anchoTotal * 0.93 - radioLuz / 2, lineaMedia - radioLuz / 2, radioLuz, radioLuz);

        graficos.restore();
    }

    private void dibujarEfecto(GraphicsContext graficos, Efecto efecto, double centroX, double centroY) {
        double progreso = efecto.calcularProgreso();
        if (efecto.tipoDeEfecto == EFECTO_SPLASH) {
            dibujarSalpicadura(graficos, centroX, centroY, progreso);
        } else if (efecto.tipoDeEfecto == EFECTO_EXPLOSION) {
            dibujarExplosion(graficos, centroX, centroY, progreso, 1.0);
        } else {
            dibujarExplosion(graficos, centroX, centroY, progreso, 1.8);
        }
    }

    private void dibujarSalpicadura(GraphicsContext graficos, double centroX, double centroY, double progreso) {
        double radioMaximo = tamanoCelda * 0.55;
        double radioActual = radioMaximo * progreso;
        double opacidad = Math.max(0, 0.78 * (1 - progreso));

        graficos.setFill(Color.rgb(220, 240, 255, opacidad));
        graficos.fillOval(centroX - radioActual, centroY - radioActual, radioActual * 2, radioActual * 2);
        graficos.setStroke(Color.rgb(255, 255, 255, opacidad));
        graficos.setLineWidth(2.0);
        graficos.strokeOval(centroX - radioActual, centroY - radioActual, radioActual * 2, radioActual * 2);

        int cantidadDeGotas = 5;
        for (int i = 0; i < cantidadDeGotas; i++) {
            double angulo = (2 * Math.PI / cantidadDeGotas) * i;
            double distancia = radioMaximo * 0.9 * progreso;
            double posX = centroX + Math.cos(angulo) * distancia;
            double posY = centroY + Math.sin(angulo) * distancia;
            double radioGota = tamanoCelda * 0.05 * (1 - progreso) + 1;
            graficos.setFill(Color.rgb(200, 230, 255, opacidad));
            graficos.fillOval(posX - radioGota, posY - radioGota, radioGota * 2, radioGota * 2);
        }
    }

    private void dibujarExplosion(GraphicsContext graficos, double centroX, double centroY, double progreso, double escala) {
        double radioMaximo = tamanoCelda * 0.7 * escala;
        double radioActual = Math.max(1, radioMaximo * Math.min(1.0, progreso * 1.6));
        double opacidad = Math.max(0, 0.86 * (1 - progreso));

        if (progreso < 0.25) {
            double opacidadDestello = 1.0 - progreso / 0.25;
            graficos.setFill(Color.rgb(255, 255, 255, opacidadDestello));
            double radioDestello = radioMaximo * 1.1;
            graficos.fillOval(centroX - radioDestello, centroY - radioDestello, radioDestello * 2, radioDestello * 2);
        }

        RadialGradient degradadoFuego = new RadialGradient(
                0, 0, centroX, centroY, radioActual, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 240, 180, opacidad)),
                new Stop(0.6, Color.rgb(255, 120, 30, opacidad)),
                new Stop(1.0, Color.rgb(120, 20, 10, 0))
        );
        graficos.setFill(degradadoFuego);
        graficos.fillOval(centroX - radioActual, centroY - radioActual, radioActual * 2, radioActual * 2);

        int cantidadParticulas = (int) (8 * escala);
        for (int i = 0; i < cantidadParticulas; i++) {
            double angulo = (2 * Math.PI / cantidadParticulas) * i + progreso * 2;
            double distancia = radioMaximo * 1.3 * progreso;
            double posX = centroX + Math.cos(angulo) * distancia;
            double posY = centroY + Math.sin(angulo) * distancia;
            double radioParticula = tamanoCelda * 0.06 * escala * (1 - progreso) + 1;
            graficos.setFill(Color.rgb(255, 180, 60, opacidad));
            graficos.fillOval(posX - radioParticula, posY - radioParticula, radioParticula * 2, radioParticula * 2);
        }

        if (progreso > 0.5) {
            double opacidadHumo = Math.max(0, 0.55 * ((progreso - 0.5) / 0.5) * (1 - progreso));
            graficos.setFill(Color.rgb(70, 70, 70, opacidadHumo));
            double radioHumo = radioMaximo * 0.6;
            double alturaSubida = tamanoCelda * 0.4 * (progreso - 0.5);
            graficos.fillOval(centroX - radioHumo, centroY - radioHumo - alturaSubida, radioHumo * 2, radioHumo * 2);
        }
    }

    private void dibujarRadar(GraphicsContext graficos) {
        double tamanoDelTableroEnPixeles = tamanoCelda * TAMANO_TABLERO;
        graficos.setFill(Color.rgb(0, 0, 0, 0.55));
        graficos.fillRect(margenIzquierdo, margenSuperior, tamanoDelTableroEnPixeles, tamanoDelTableroEnPixeles);

        double centroX = margenIzquierdo + tamanoDelTableroEnPixeles / 2;
        double centroY = margenSuperior + tamanoDelTableroEnPixeles / 2;
        double radioMaximo = tamanoDelTableroEnPixeles / 2;

        graficos.setLineWidth(1.5);
        graficos.setStroke(Color.color(colorDelRadar.getRed(), colorDelRadar.getGreen(), colorDelRadar.getBlue(), 0.35));
        for (int i = 1; i <= 4; i++) {
            double radioAnillo = radioMaximo * i / 4.0;
            graficos.strokeOval(centroX - radioAnillo, centroY - radioAnillo, radioAnillo * 2, radioAnillo * 2);
        }
        graficos.strokeLine(centroX - radioMaximo, centroY, centroX + radioMaximo, centroY);
        graficos.strokeLine(centroX, centroY - radioMaximo, centroX, centroY + radioMaximo);

        // Sector de barrido del radar (ángulo invertido para concordar con sistema cartesiano de JavaFX)
        graficos.setFill(Color.color(colorDelRadar.getRed(), colorDelRadar.getGreen(), colorDelRadar.getBlue(), 0.35));
        graficos.fillArc(centroX - radioMaximo, centroY - radioMaximo, radioMaximo * 2, radioMaximo * 2,
                -anguloDelRadar, 40, ArcType.ROUND);

        // Línea de barrido
        graficos.setStroke(colorDelRadar);
        graficos.setLineWidth(2.0);
        double anguloRad = Math.toRadians(anguloDelRadar);
        double extremoX = centroX + Math.cos(anguloRad) * radioMaximo;
        double extremoY = centroY + Math.sin(anguloRad) * radioMaximo;
        graficos.strokeLine(centroX, centroY, extremoX, extremoY);

        // Parpadeo del objetivo escaneado
        double posXObjetivo = margenIzquierdo + columnaDelRadar * tamanoCelda + tamanoCelda / 2;
        double posYObjetivo = margenSuperior + filaDelRadar * tamanoCelda + tamanoCelda / 2;
        boolean objetivoVisible = (tiempoTranscurridoDelRadar / 150) % 2 == 0;
        if (objetivoVisible) {
            graficos.setFill(colorDelRadar);
            double radioObjetivo = tamanoCelda / 6;
            graficos.fillOval(posXObjetivo - radioObjetivo, posYObjetivo - radioObjetivo, radioObjetivo * 2, radioObjetivo * 2);
        }

        // Texto informativo del radar
        graficos.setFill(Color.WHITE);
        graficos.setFont(Font.font("Monospaced", FontWeight.BOLD, Math.max(12, tamanoCelda / 2)));
        graficos.setTextAlign(TextAlignment.CENTER);
        graficos.fillText("ESCANEANDO...", centroX, margenSuperior + tamanoDelTableroEnPixeles + 20);
    }
}