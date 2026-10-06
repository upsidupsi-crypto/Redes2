import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
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
    private static final int MILISEGUNDOS_ENTRE_CUADROS = 30;

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
    private int filaDelRadar;
    private int columnaDelRadar;
    private Color colorDelRadar;
    private Runnable accionAlTerminarElRadar;

    private int tamanoCelda;
    private int margenIzquierdo;
    private int margenSuperior;

    private final Timer temporizadorDeAnimacion;

    public PanelTablero() {
        estadosDeCasillas = new char[TAMANO_TABLERO][TAMANO_TABLERO];
        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            Arrays.fill(estadosDeCasillas[fila], LogicaBarcos.CASILLA_AGUA);
        }
        barcosVisibles = new ArrayList<LogicaBarcos.Barco>();
        efectosActivos = new ArrayList<Efecto>();

        interactivo = false;
        oyenteDeCasilla = null;

        radarActivo = false;
        anguloDelRadar = 0;
        tiempoTranscurridoDelRadar = 0;
        filaDelRadar = 0;
        columnaDelRadar = 0;
        colorDelRadar = new Color(60, 220, 90);
        accionAlTerminarElRadar = null;

        tamanoCelda = 30;
        margenIzquierdo = 20;
        margenSuperior = 20;

        temporizadorDeAnimacion = new Timer(MILISEGUNDOS_ENTRE_CUADROS, new ActionListener() {
            public void actionPerformed(ActionEvent evento) {
                avanzarAnimaciones();
            }
        });
        temporizadorDeAnimacion.start();

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evento) {
                if (!interactivo || radarActivo || oyenteDeCasilla == null) {
                    return;
                }
                int posicionXEnTablero = evento.getX() - margenIzquierdo;
                int posicionYEnTablero = evento.getY() - margenSuperior;
                if (posicionXEnTablero < 0 || posicionYEnTablero < 0) {
                    return;
                }
                int columna = posicionXEnTablero / tamanoCelda;
                int fila = posicionYEnTablero / tamanoCelda;
                if (fila < TAMANO_TABLERO && columna < TAMANO_TABLERO) {
                    oyenteDeCasilla.alSeleccionarCasilla(fila, columna);
                }
            }
        });
    }

    private void avanzarAnimaciones() {
        boolean necesitaRepintar = false;

        if (radarActivo) {
            anguloDelRadar = anguloDelRadar + 12;
            tiempoTranscurridoDelRadar = tiempoTranscurridoDelRadar + MILISEGUNDOS_ENTRE_CUADROS;
            if (tiempoTranscurridoDelRadar >= DURACION_RADAR_MILISEGUNDOS) {
                radarActivo = false;
                Runnable accionPendiente = accionAlTerminarElRadar;
                accionAlTerminarElRadar = null;
                if (accionPendiente != null) {
                    accionPendiente.run();
                }
            }
            necesitaRepintar = true;
        }

        if (!efectosActivos.isEmpty()) {
            Iterator<Efecto> iteradorDeEfectos = efectosActivos.iterator();
            while (iteradorDeEfectos.hasNext()) {
                Efecto efecto = iteradorDeEfectos.next();
                if (efecto.haTerminado()) {
                    iteradorDeEfectos.remove();
                }
            }
            necesitaRepintar = true;
        }

        if (necesitaRepintar) {
            repaint();
        }
    }

    public void actualizar(char[][] nuevosEstados, List<LogicaBarcos.Barco> nuevosBarcosVisibles) {
        this.estadosDeCasillas = nuevosEstados;
        this.barcosVisibles = nuevosBarcosVisibles;
        repaint();
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

    public void detener() {
        super.detener();
        temporizadorDeAnimacion.stop();
    }

    public void agregarEfecto(int tipoDeEfecto, int fila, int columna) {
        efectosActivos.add(new Efecto(tipoDeEfecto, fila, columna));
        repaint();
    }

    public void mostrarRadar(int fila, int columna, Color color, Runnable accionAlTerminar) {
        this.filaDelRadar = fila;
        this.columnaDelRadar = columna;
        this.colorDelRadar = color;
        this.anguloDelRadar = 0;
        this.tiempoTranscurridoDelRadar = 0;
        this.accionAlTerminarElRadar = accionAlTerminar;
        this.radarActivo = true;
    }

    private void calcularGeometria() {
        int anchoDelPanel = getWidth();
        int altoDelPanel = getHeight();
        int espacioDisponible = Math.min(anchoDelPanel, altoDelPanel) - 30;
        tamanoCelda = Math.max(12, espacioDisponible / TAMANO_TABLERO);
        int tamanoDelTableroEnPixeles = tamanoCelda * TAMANO_TABLERO;
        margenIzquierdo = (anchoDelPanel - tamanoDelTableroEnPixeles) / 2;
        margenSuperior = Math.max(24, (altoDelPanel - tamanoDelTableroEnPixeles) / 2);
    }

    protected void dibujarContenido(Graphics2D graficos) {
        calcularGeometria();

        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            for (int columna = 0; columna < TAMANO_TABLERO; columna++) {
                int posicionX = margenIzquierdo + columna * tamanoCelda;
                int posicionY = margenSuperior + fila * tamanoCelda;
                dibujarCasilla(graficos, posicionX, posicionY, estadosDeCasillas[fila][columna]);
            }
        }

        for (LogicaBarcos.Barco barco : barcosVisibles) {
            dibujarBarcoCompleto(graficos, barco);
        }

        dibujarEtiquetasDeCoordenadas(graficos);

        for (Efecto efecto : efectosActivos) {
            int centroX = margenIzquierdo + efecto.columna * tamanoCelda + tamanoCelda / 2;
            int centroY = margenSuperior + efecto.fila * tamanoCelda + tamanoCelda / 2;
            dibujarEfecto(graficos, efecto, centroX, centroY);
        }

        if (radarActivo) {
            dibujarRadar(graficos);
        }
    }

    private void dibujarCasilla(Graphics2D graficos, int posicionX, int posicionY, char estadoDeCasilla) {
        graficos.setColor(new Color(255, 255, 255, 25));
        graficos.fillRect(posicionX, posicionY, tamanoCelda, tamanoCelda);
        graficos.setColor(new Color(255, 255, 255, 60));
        graficos.drawRect(posicionX, posicionY, tamanoCelda, tamanoCelda);

        int centroX = posicionX + tamanoCelda / 2;
        int centroY = posicionY + tamanoCelda / 2;

        if (estadoDeCasilla == LogicaBarcos.CASILLA_FALLO) {
            int radioDelCirculo = (int) (tamanoCelda * 0.12);
            graficos.setColor(new Color(230, 240, 255, 190));
            graficos.setStroke(new BasicStroke(2f));
            graficos.drawOval(centroX - radioDelCirculo, centroY - radioDelCirculo, radioDelCirculo * 2, radioDelCirculo * 2);
        } else if (estadoDeCasilla == LogicaBarcos.CASILLA_TOCADA || estadoDeCasilla == LogicaBarcos.CASILLA_HUNDIDA) {
            int radioDeLaMarca = (int) (tamanoCelda * 0.26);
            graficos.setColor(new Color(40, 20, 10, 170));
            graficos.fillOval(centroX - radioDeLaMarca, centroY - radioDeLaMarca, radioDeLaMarca * 2, radioDeLaMarca * 2);
            graficos.setColor(new Color(255, 100, 30, 210));
            graficos.setStroke(new BasicStroke(2f));
            graficos.drawLine(centroX - radioDeLaMarca, centroY - radioDeLaMarca, centroX + radioDeLaMarca, centroY + radioDeLaMarca);
            graficos.drawLine(centroX - radioDeLaMarca, centroY + radioDeLaMarca, centroX + radioDeLaMarca, centroY - radioDeLaMarca);
        }
    }

    private void dibujarBarcoCompleto(Graphics2D graficos, LogicaBarcos.Barco barco) {
        int posicionX = margenIzquierdo + barco.getColumnaInicial() * tamanoCelda;
        int posicionY = margenSuperior + barco.getFilaInicial() * tamanoCelda;
        dibujarSilueta(graficos, posicionX, posicionY, tamanoCelda, barco.getLongitud(), barco.isHorizontal());
    }

    private void dibujarEtiquetasDeCoordenadas(Graphics2D graficos) {
        graficos.setColor(new Color(255, 255, 255, 210));
        graficos.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, tamanoCelda / 3)));
        for (int columna = 0; columna < TAMANO_TABLERO; columna++) {
            String textoDeColumna = String.valueOf(columna + 1);
            graficos.drawString(textoDeColumna, margenIzquierdo + columna * tamanoCelda + tamanoCelda / 2 - 4, margenSuperior - 8);
        }
        for (int fila = 0; fila < TAMANO_TABLERO; fila++) {
            String textoDeFila = String.valueOf((char) ('A' + fila));
            graficos.drawString(textoDeFila, margenIzquierdo - 18, margenSuperior + fila * tamanoCelda + tamanoCelda / 2 + 5);
        }
    }

    private static void dibujarSilueta(Graphics2D graficosOriginales, int posicionXDeCelda, int posicionYDeCelda,
                                       int tamanoDeCelda, int longitudDelBarco, boolean horizontal) {
        Graphics2D graficos = (Graphics2D) graficosOriginales.create();
        graficos.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graficos.translate(posicionXDeCelda, posicionYDeCelda);
        if (!horizontal) {
            graficos.rotate(Math.PI / 2);
            graficos.translate(0, -tamanoDeCelda);
        }

        int anchoTotal = longitudDelBarco * tamanoDeCelda;
        double bordeSuperior = tamanoDeCelda * 0.16;
        double bordeInferior = tamanoDeCelda * 0.84;
        double lineaMedia = tamanoDeCelda * 0.5;

        GeneralPath casco = new GeneralPath();
        casco.moveTo(anchoTotal * 0.04, bordeSuperior);
        casco.lineTo(anchoTotal * 0.82, bordeSuperior);
        casco.lineTo(anchoTotal * 0.98, lineaMedia);
        casco.lineTo(anchoTotal * 0.82, bordeInferior);
        casco.lineTo(anchoTotal * 0.04, bordeInferior);
        casco.lineTo(0, lineaMedia);
        casco.closePath();

        graficos.setPaint(new GradientPaint(0, (float) bordeSuperior, new Color(205, 208, 212),
                0, (float) bordeInferior, new Color(85, 90, 98)));
        graficos.fill(casco);

        graficos.setColor(new Color(35, 38, 45));
        graficos.setStroke(new BasicStroke(Math.max(1f, (float) (tamanoDeCelda * 0.03))));
        graficos.draw(casco);

        graficos.setColor(new Color(255, 255, 255, 90));
        graficos.setStroke(new BasicStroke(Math.max(1f, (float) (tamanoDeCelda * 0.015))));
        graficos.drawLine((int) (anchoTotal * 0.06), (int) (bordeSuperior + tamanoDeCelda * 0.05),
                (int) (anchoTotal * 0.8), (int) (bordeSuperior + tamanoDeCelda * 0.05));

        int cantidadDeBloques = 1;
        double anchoDeBloque = anchoTotal * 0.28;
        if (longitudDelBarco >= 4) {
            cantidadDeBloques = 2;
            anchoDeBloque = anchoTotal * 0.18;
        }
        double altoDeBloque = tamanoDeCelda * 0.36;
        for (int numeroDeBloque = 0; numeroDeBloque < cantidadDeBloques; numeroDeBloque++) {
            double centroDelBloque = anchoTotal * 0.5;
            if (cantidadDeBloques == 2) {
                centroDelBloque = anchoTotal * (0.35 + numeroDeBloque * 0.3);
            }
            double posicionXDelBloque = centroDelBloque - anchoDeBloque / 2;
            double posicionYDelBloque = lineaMedia - altoDeBloque / 2;
            RoundRectangle2D bloque = new RoundRectangle2D.Double(posicionXDelBloque, posicionYDelBloque,
                    anchoDeBloque, altoDeBloque, tamanoDeCelda * 0.08, tamanoDeCelda * 0.08);
            graficos.setPaint(new GradientPaint(0, (float) posicionYDelBloque, new Color(235, 236, 238),
                    0, (float) (posicionYDelBloque + altoDeBloque), new Color(150, 153, 158)));
            graficos.fill(bloque);
            graficos.setColor(new Color(35, 38, 45));
            graficos.setStroke(new BasicStroke(Math.max(1f, (float) (tamanoDeCelda * 0.02))));
            graficos.draw(bloque);
        }

        double posicionDelMastil = anchoTotal * 0.5;
        if (cantidadDeBloques == 2) {
            posicionDelMastil = anchoTotal * 0.65;
        }
        graficos.setColor(new Color(60, 62, 68));
        graficos.setStroke(new BasicStroke(Math.max(1f, (float) (tamanoDeCelda * 0.02))));
        graficos.drawLine((int) posicionDelMastil, (int) (lineaMedia - altoDeBloque / 2),
                (int) posicionDelMastil, (int) (bordeSuperior - tamanoDeCelda * 0.02));
        int radioDeLaAntena = (int) (tamanoDeCelda * 0.05);
        graficos.fillOval((int) (posicionDelMastil - radioDeLaAntena), (int) (bordeSuperior - tamanoDeCelda * 0.02) - radioDeLaAntena,
                radioDeLaAntena * 2, radioDeLaAntena * 2);

        if (longitudDelBarco >= 3) {
            double[] posicionesDeCanones = {0.12};
            if (longitudDelBarco >= 4) {
                posicionesDeCanones = new double[]{0.12, 0.9};
            }
            for (double fraccionDePosicion : posicionesDeCanones) {
                double posicionDelCanon = anchoTotal * fraccionDePosicion;
                double anchoDelCanon = tamanoDeCelda * 0.12;
                double altoDelCanon = tamanoDeCelda * 0.22;
                graficos.setColor(new Color(50, 52, 58));
                graficos.fillRoundRect((int) (posicionDelCanon - anchoDelCanon / 2), (int) (lineaMedia - altoDelCanon / 2),
                        (int) anchoDelCanon, (int) altoDelCanon, 3, 3);
            }
        }

        graficos.setColor(new Color(220, 40, 40));
        int radioDeLaLuz = (int) (tamanoDeCelda * 0.08);
        graficos.fillOval((int) (anchoTotal * 0.93) - radioDeLaLuz / 2, (int) (lineaMedia - radioDeLaLuz / 2), radioDeLaLuz, radioDeLaLuz);

        graficos.dispose();
    }

    private void dibujarEfecto(Graphics2D graficos, Efecto efecto, int centroX, int centroY) {
        double progreso = efecto.calcularProgreso();
        if (efecto.tipoDeEfecto == EFECTO_SPLASH) {
            dibujarSalpicadura(graficos, centroX, centroY, progreso);
        } else if (efecto.tipoDeEfecto == EFECTO_EXPLOSION) {
            dibujarExplosion(graficos, centroX, centroY, progreso, 1.0);
        } else {
            dibujarExplosion(graficos, centroX, centroY, progreso, 1.8);
        }
    }

    private void dibujarSalpicadura(Graphics2D graficos, int centroX, int centroY, double progreso) {
        int radioMaximo = (int) (tamanoCelda * 0.55);
        int radioActual = (int) (radioMaximo * progreso);
        int transparencia = Math.max(0, (int) (200 * (1 - progreso)));

        graficos.setColor(new Color(220, 240, 255, transparencia));
        graficos.fillOval(centroX - radioActual, centroY - radioActual, radioActual * 2, radioActual * 2);
        graficos.setColor(new Color(255, 255, 255, transparencia));
        graficos.setStroke(new BasicStroke(2f));
        graficos.drawOval(centroX - radioActual, centroY - radioActual, radioActual * 2, radioActual * 2);

        int cantidadDeGotas = 5;
        for (int numeroDeGota = 0; numeroDeGota < cantidadDeGotas; numeroDeGota++) {
            double angulo = (2 * Math.PI / cantidadDeGotas) * numeroDeGota;
            int distanciaDelCentro = (int) (radioMaximo * 0.9 * progreso);
            int posicionXDeGota = centroX + (int) (Math.cos(angulo) * distanciaDelCentro);
            int posicionYDeGota = centroY + (int) (Math.sin(angulo) * distanciaDelCentro);
            int radioDeGota = (int) (tamanoCelda * 0.05 * (1 - progreso)) + 1;
            graficos.setColor(new Color(200, 230, 255, transparencia));
            graficos.fillOval(posicionXDeGota - radioDeGota, posicionYDeGota - radioDeGota, radioDeGota * 2, radioDeGota * 2);
        }
    }

    private void dibujarExplosion(Graphics2D graficos, int centroX, int centroY, double progreso, double escala) {
        int radioMaximo = (int) (tamanoCelda * 0.7 * escala);
        int radioActual = Math.max(1, (int) (radioMaximo * Math.min(1.0, progreso * 1.6)));
        int transparenciaDelCuerpo = Math.max(0, (int) (220 * (1 - progreso)));

        if (progreso < 0.25) {
            int transparenciaDelDestello = (int) (255 * (1 - progreso / 0.25));
            graficos.setColor(new Color(255, 255, 255, transparenciaDelDestello));
            int radioDelDestello = (int) (radioMaximo * 1.1);
            graficos.fillOval(centroX - radioDelDestello, centroY - radioDelDestello, radioDelDestello * 2, radioDelDestello * 2);
        }

        RadialGradientPaint degradadoDeFuego = new RadialGradientPaint(
                new Point(centroX, centroY), radioActual,
                new float[]{0f, 0.6f, 1f},
                new Color[]{
                        new Color(255, 240, 180, transparenciaDelCuerpo),
                        new Color(255, 120, 30, transparenciaDelCuerpo),
                        new Color(120, 20, 10, 0)
                });
        graficos.setPaint(degradadoDeFuego);
        graficos.fillOval(centroX - radioActual, centroY - radioActual, radioActual * 2, radioActual * 2);

        int cantidadDeParticulas = (int) (8 * escala);
        for (int numeroDeParticula = 0; numeroDeParticula < cantidadDeParticulas; numeroDeParticula++) {
            double angulo = (2 * Math.PI / cantidadDeParticulas) * numeroDeParticula + progreso * 2;
            int distanciaDelCentro = (int) (radioMaximo * 1.3 * progreso);
            int posicionXDeParticula = centroX + (int) (Math.cos(angulo) * distanciaDelCentro);
            int posicionYDeParticula = centroY + (int) (Math.sin(angulo) * distanciaDelCentro);
            int radioDeParticula = (int) (tamanoCelda * 0.06 * escala * (1 - progreso)) + 1;
            graficos.setColor(new Color(255, 180, 60, transparenciaDelCuerpo));
            graficos.fillOval(posicionXDeParticula - radioDeParticula, posicionYDeParticula - radioDeParticula,
                    radioDeParticula * 2, radioDeParticula * 2);
        }

        if (progreso > 0.5) {
            int transparenciaDelHumo = Math.max(0, (int) (140 * ((progreso - 0.5) / 0.5) * (1 - progreso)));
            graficos.setColor(new Color(70, 70, 70, transparenciaDelHumo));
            int radioDelHumo = (int) (radioMaximo * 0.6);
            int alturaDeSubida = (int) (tamanoCelda * 0.4 * (progreso - 0.5));
            graficos.fillOval(centroX - radioDelHumo, centroY - radioDelHumo - alturaDeSubida, radioDelHumo * 2, radioDelHumo * 2);
        }
    }

    private void dibujarRadar(Graphics2D graficos) {
        int tamanoDelTableroEnPixeles = tamanoCelda * TAMANO_TABLERO;
        graficos.setColor(new Color(0, 0, 0, 140));
        graficos.fillRect(margenIzquierdo, margenSuperior, tamanoDelTableroEnPixeles, tamanoDelTableroEnPixeles);

        int centroX = margenIzquierdo + tamanoDelTableroEnPixeles / 2;
        int centroY = margenSuperior + tamanoDelTableroEnPixeles / 2;
        int radioMaximo = tamanoDelTableroEnPixeles / 2;

        graficos.setStroke(new BasicStroke(1.5f));
        graficos.setColor(new Color(colorDelRadar.getRed(), colorDelRadar.getGreen(), colorDelRadar.getBlue(), 90));
        for (int numeroDeAnillo = 1; numeroDeAnillo <= 4; numeroDeAnillo++) {
            int radioDelAnillo = radioMaximo * numeroDeAnillo / 4;
            graficos.drawOval(centroX - radioDelAnillo, centroY - radioDelAnillo, radioDelAnillo * 2, radioDelAnillo * 2);
        }
        graficos.drawLine(centroX - radioMaximo, centroY, centroX + radioMaximo, centroY);
        graficos.drawLine(centroX, centroY - radioMaximo, centroX, centroY + radioMaximo);

        Arc2D.Double sectorDeBarrido = new Arc2D.Double(centroX - radioMaximo, centroY - radioMaximo,
                radioMaximo * 2, radioMaximo * 2, anguloDelRadar, 40, Arc2D.PIE);
        graficos.setColor(new Color(colorDelRadar.getRed(), colorDelRadar.getGreen(), colorDelRadar.getBlue(), 90));
        graficos.fill(sectorDeBarrido);

        graficos.setColor(colorDelRadar);
        graficos.setStroke(new BasicStroke(2f));
        double anguloEnRadianes = Math.toRadians(-anguloDelRadar);
        int extremoDeLaLineaX = centroX + (int) (Math.cos(anguloEnRadianes) * radioMaximo);
        int extremoDeLaLineaY = centroY + (int) (Math.sin(anguloEnRadianes) * radioMaximo);
        graficos.drawLine(centroX, centroY, extremoDeLaLineaX, extremoDeLaLineaY);

        int posicionDelObjetivoX = margenIzquierdo + columnaDelRadar * tamanoCelda + tamanoCelda / 2;
        int posicionDelObjetivoY = margenSuperior + filaDelRadar * tamanoCelda + tamanoCelda / 2;
        boolean objetivoVisible = (tiempoTranscurridoDelRadar / 150) % 2 == 0;
        if (objetivoVisible) {
            graficos.setColor(colorDelRadar);
            int radioDelObjetivo = tamanoCelda / 6;
            graficos.fillOval(posicionDelObjetivoX - radioDelObjetivo, posicionDelObjetivoY - radioDelObjetivo,
                    radioDelObjetivo * 2, radioDelObjetivo * 2);
        }

        graficos.setColor(Color.WHITE);
        graficos.setFont(new Font("Monospaced", Font.BOLD, Math.max(12, tamanoCelda / 2)));
        String textoDelRadar = "ESCANEANDO...";
        FontMetrics medidasDeLaFuente = graficos.getFontMetrics();
        graficos.drawString(textoDelRadar, centroX - medidasDeLaFuente.stringWidth(textoDelRadar) / 2, margenSuperior + tamanoDelTableroEnPixeles + 20);
    }
}