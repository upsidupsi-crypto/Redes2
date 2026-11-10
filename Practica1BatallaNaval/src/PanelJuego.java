import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class PanelJuego extends PanelBase {

    private static final Color COLOR_RADAR_JUGADOR = Color.web("#3ce66e");
    private static final Color COLOR_RADAR_SERVIDOR = Color.web("#e64646");

    private static final int FASE_COLOCACION = 0;
    private static final int FASE_BATALLA = 1;
    private static final int FASE_TERMINADA = 2;

    private static final int PAUSA_ANTES_DEL_MENSAJE_FINAL_MILISEGUNDOS = 1400;

    private final Cliente cliente;
    private final Runnable accionVolverAlMenu;
    private LogicaBarcos logicaJugador;

    private final PanelTablero panelDeColocacion = new PanelTablero();
    private final PanelTablero panelDeFlotaPropia = new PanelTablero();
    private final PanelTablero panelDeTableroEnemigo = new PanelTablero();

    private final Label etiquetaDeEstado = new Label("Coloca tus barcos");
    private final Label etiquetaDeColocacion = new Label("");
    private final Button botonComenzarBatalla = new Button("Comenzar batalla");

    private final StackPane panelCentral = new StackPane();
    private final VBox vistaDeColocacion;
    private final HBox vistaDeBatalla;

    private int indiceBarcoPendiente;
    private boolean orientacionHorizontal;

    private volatile int faseActual;
    private volatile boolean partidaDetenida;
    private volatile int filaSeleccionada;
    private volatile int columnaSeleccionada;

    private final Semaphore semaforoDeClic = new Semaphore(0);
    private final Semaphore semaforoDeAnimacion = new Semaphore(0);
    private Thread hiloDeRed;

    public PanelJuego(Cliente cliente, Runnable accionVolverAlMenu) {
        this.cliente = cliente;
        this.accionVolverAlMenu = accionVolverAlMenu;
        this.logicaJugador = new LogicaBarcos();
        this.indiceBarcoPendiente = 0;
        this.orientacionHorizontal = true;
        this.faseActual = FASE_COLOCACION;
        this.partidaDetenida = false;

        // Estructura principal encima del canvas animado de PanelBase
        BorderPane layoutPrincipal = new BorderPane();
        layoutPrincipal.setTop(construirBarraSuperior());

        vistaDeColocacion = construirVistaDeColocacion();
        vistaDeBatalla = construirVistaDeBatalla();

        vistaDeBatalla.setVisible(false); // Inicialmente oculta

        panelCentral.getChildren().addAll(vistaDeColocacion, vistaDeBatalla);
        layoutPrincipal.setCenter(panelCentral);

        getChildren().add(layoutPrincipal);

        // Configuración de eventos en tableros
        panelDeColocacion.setOyenteDeCasilla(this::alSeleccionarCasillaDeColocacion);
        panelDeTableroEnemigo.setOyenteDeCasilla(this::alSeleccionarCasillaEnemiga);

        panelDeColocacion.setInteractivo(true);
        panelDeFlotaPropia.setInteractivo(false);
        panelDeTableroEnemigo.setInteractivo(false);

        actualizarTextoDeColocacion();
        actualizarTablerosDeColocacion();
    }

    private BorderPane construirBarraSuperior() {
        BorderPane barraSuperior = new BorderPane();
        barraSuperior.setPadding(new Insets(10, 16, 10, 16));

        etiquetaDeEstado.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
        etiquetaDeEstado.setTextFill(Color.WHITE);

        Button botonMenu = new Button("← Menú");
        botonMenu.setStyle("-fx-font-size: 14px; -fx-cursor: hand;");
        botonMenu.setFocusTraversable(false);
        botonMenu.setOnAction(evento -> {
            detener();
            accionVolverAlMenu.run();
        });

        barraSuperior.setLeft(botonMenu);
        barraSuperior.setCenter(etiquetaDeEstado);
        BorderPane.setAlignment(etiquetaDeEstado, Pos.CENTER);
        return barraSuperior;
    }

    private VBox construirVistaDeColocacion() {
        VBox contenedor = new VBox(10);
        contenedor.setAlignment(Pos.CENTER);

        etiquetaDeColocacion.setTextFill(Color.WHITE);
        etiquetaDeColocacion.setFont(Font.font("SansSerif", 15));

        HBox filaDeBotones = new HBox(10);
        filaDeBotones.setAlignment(Pos.CENTER);

        Button botonGirar = new Button("Girar orientación");
        botonGirar.setStyle("-fx-cursor: hand;");
        botonGirar.setOnAction(evento -> {
            orientacionHorizontal = !orientacionHorizontal;
            actualizarTextoDeColocacion();
        });

        Button botonColocacionAleatoria = new Button("Colocación aleatoria");
        botonColocacionAleatoria.setStyle("-fx-cursor: hand;");
        botonColocacionAleatoria.setOnAction(evento -> {
            logicaJugador = new LogicaBarcos();
            logicaJugador.colocarFlotaAleatoria();
            indiceBarcoPendiente = LogicaBarcos.LONGITUDES_BARCOS.length;
            Sonido.reproducir("colocar");
            actualizarTablerosDeColocacion();
            actualizarTextoDeColocacion();
            botonComenzarBatalla.setDisable(false);
        });

        Button botonReiniciarColocacion = new Button("Reiniciar colocación");
        botonReiniciarColocacion.setStyle("-fx-cursor: hand;");
        botonReiniciarColocacion.setOnAction(evento -> {
            logicaJugador = new LogicaBarcos();
            indiceBarcoPendiente = 0;
            Sonido.reproducir("click");
            actualizarTablerosDeColocacion();
            actualizarTextoDeColocacion();
            botonComenzarBatalla.setDisable(true);
        });

        botonComenzarBatalla.setDisable(true);
        botonComenzarBatalla.setStyle("-fx-cursor: hand; -fx-font-weight: bold;");
        botonComenzarBatalla.setOnAction(evento -> iniciarBatalla());

        filaDeBotones.getChildren().addAll(botonGirar, botonColocacionAleatoria, botonReiniciarColocacion, botonComenzarBatalla);

        VBox controles = new VBox(8);
        controles.setAlignment(Pos.CENTER);
        controles.setPadding(new Insets(10));
        controles.getChildren().addAll(etiquetaDeColocacion, filaDeBotones);

        contenedor.getChildren().addAll(panelDeColocacion, controles);
        VBox.setVgrow(panelDeColocacion, javafx.scene.layout.Priority.ALWAYS);
        return contenedor;
    }

    private HBox construirVistaDeBatalla() {
        HBox contenedor = new HBox(15);
        contenedor.setAlignment(Pos.CENTER);
        contenedor.setPadding(new Insets(0, 10, 10, 10));

        VBox cajaFlotaPropia = envolverConTitulo(panelDeFlotaPropia, "Tu flota");
        VBox cajaTableroEnemigo = envolverConTitulo(panelDeTableroEnemigo, "Tablero enemigo");

        HBox.setHgrow(cajaFlotaPropia, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(cajaTableroEnemigo, javafx.scene.layout.Priority.ALWAYS);

        contenedor.getChildren().addAll(cajaFlotaPropia, cajaTableroEnemigo);
        return contenedor;
    }

    private VBox envolverConTitulo(PanelTablero tablero, String titulo) {
        VBox contenedor = new VBox(5);
        contenedor.setAlignment(Pos.CENTER);
        Label etiquetaTitulo = new Label(titulo);
        etiquetaTitulo.setTextFill(Color.WHITE);
        etiquetaTitulo.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));

        contenedor.getChildren().addAll(etiquetaTitulo, tablero);
        VBox.setVgrow(tablero, javafx.scene.layout.Priority.ALWAYS);
        return contenedor;
    }

    private void actualizarTablerosDeColocacion() {
        panelDeColocacion.actualizar(logicaJugador.getTableroPropio(), logicaJugador.getBarcosPropios());
    }

    private void actualizarTextoDeColocacion() {
        if (indiceBarcoPendiente >= LogicaBarcos.LONGITUDES_BARCOS.length) {
            etiquetaDeColocacion.setText("Flota completa. Pulsa \"Comenzar batalla\".");
            return;
        }
        String textoOrientacion = orientacionHorizontal ? "Horizontal" : "Vertical";
        etiquetaDeColocacion.setText("Coloca: " + LogicaBarcos.NOMBRES_BARCOS[indiceBarcoPendiente]
                + " (" + LogicaBarcos.LONGITUDES_BARCOS[indiceBarcoPendiente] + " casillas) - " + textoOrientacion);
    }

    private void alSeleccionarCasillaDeColocacion(int fila, int columna) {
        if (faseActual != FASE_COLOCACION || indiceBarcoPendiente >= LogicaBarcos.LONGITUDES_BARCOS.length) {
            return;
        }
        boolean barcoColocado = logicaJugador.colocarBarco(
                LogicaBarcos.NOMBRES_BARCOS[indiceBarcoPendiente],
                LogicaBarcos.LONGITUDES_BARCOS[indiceBarcoPendiente],
                fila, columna, orientacionHorizontal);

        if (barcoColocado) {
            Sonido.reproducir("colocar");
            indiceBarcoPendiente++;
            actualizarTablerosDeColocacion();
            actualizarTextoDeColocacion();
            if (indiceBarcoPendiente >= LogicaBarcos.LONGITUDES_BARCOS.length) {
                botonComenzarBatalla.setDisable(false);
            }
        } else {
            etiquetaDeColocacion.setText("Posición inválida: el barco se sale del tablero o se encima con otro.");
        }
    }

    private void iniciarBatalla() {
        faseActual = FASE_BATALLA;
        botonComenzarBatalla.setDisable(true);
        panelDeColocacion.setInteractivo(false);

        panelDeColocacion.actualizar(logicaJugador.copiarTableroPropio(), logicaJugador.getBarcosPropios());
        panelDeFlotaPropia.actualizar(logicaJugador.copiarTableroPropio(), logicaJugador.getBarcosPropios());
        panelDeTableroEnemigo.actualizar(logicaJugador.copiarTableroDeTiro(), new ArrayList<>());

        etiquetaDeEstado.setText("Esperando al servidor...");
        vistaDeColocacion.setVisible(false);
        vistaDeBatalla.setVisible(true);

        hiloDeRed = new Thread(this::ejecutarPartida);
        hiloDeRed.setDaemon(true);
        hiloDeRed.start();
    }

    private void ejecutarPartida() {
        try {
            boolean turnoDelJugador = cliente.enviarListoYEsperarTurno();
            boolean partidaTerminada = false;

            while (!partidaTerminada && !partidaDetenida) {
                if (turnoDelJugador) {
                    partidaTerminada = jugarTurnoDelJugador();
                } else {
                    partidaTerminada = jugarTurnoDelServidor();
                }
                turnoDelJugador = !turnoDelJugador;
            }
        } catch (Exception excepcion) {
            if (!partidaDetenida) {
                mostrarErrorDeConexion(excepcion.getMessage());
            }
        }
    }

    private boolean jugarTurnoDelJugador() throws IOException, ClassNotFoundException, InterruptedException {
        int disparosRealizados = 0;
        boolean jugadorFalloElDisparo = false;
        boolean partidaTerminada = false;

        while (disparosRealizados < LogicaBarcos.DISPAROS_POR_TURNO && !jugadorFalloElDisparo && !partidaTerminada) {
            habilitarTableroEnemigo("Tu turno: elige una casilla del tablero enemigo (disparo "
                    + (disparosRealizados + 1) + " de " + LogicaBarcos.DISPAROS_POR_TURNO + ")");
            semaforoDeClic.acquire();
            int filaDisparada = filaSeleccionada;
            int columnaDisparada = columnaSeleccionada;

            Mensaje mensajeResultado = cliente.disparar(filaDisparada, columnaDisparada);
            String resultado = mensajeResultado.getTexto();

            if (LogicaBarcos.RESULTADO_REPETIDO.equals(resultado) || LogicaBarcos.RESULTADO_INVALIDO.equals(resultado)) {
                continue;
            }

            boolean resultadoValido = LogicaBarcos.RESULTADO_AGUA.equals(resultado)
                    || LogicaBarcos.RESULTADO_TOCADO.equals(resultado)
                    || LogicaBarcos.RESULTADO_HUNDIDO.equals(resultado);
            if (!resultadoValido) {
                throw new IOException("El servidor respondió con un resultado inválido: " + resultado);
            }

            logicaJugador.registrarResultadoDisparo(filaDisparada, columnaDisparada, resultado);
            if (LogicaBarcos.RESULTADO_HUNDIDO.equals(resultado)) {
                if (!mensajeResultado.tieneBarcoHundido()) {
                    throw new IOException("El servidor no indicó cuál barco se hundió");
                }
                logicaJugador.registrarBarcoEnemigoHundido(mensajeResultado.obtenerBarcoHundido());
            }

            disparosRealizados++;
            if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
                jugadorFalloElDisparo = true;
            }
            if (mensajeResultado.isFinDeJuego()) {
                partidaTerminada = true;
            }
            boolean elTurnoContinua = !jugadorFalloElDisparo && !partidaTerminada
                    && disparosRealizados < LogicaBarcos.DISPAROS_POR_TURNO;

            char[][] copiaDelTableroDeTiro = logicaJugador.copiarTableroDeTiro();
            List<LogicaBarcos.Barco> copiaDeBarcosEnemigosHundidos = logicaJugador.copiarBarcosEnemigosHundidos();
            String textoDeResultado = construirTextoDelDisparoDelJugador(filaDisparada, columnaDisparada, resultado,
                    mensajeResultado.getNombreBarcoHundido(), partidaTerminada, elTurnoContinua);

            mostrarDisparoEnPantalla(panelDeTableroEnemigo, filaDisparada, columnaDisparada, COLOR_RADAR_JUGADOR,
                    resultado, copiaDelTableroDeTiro, copiaDeBarcosEnemigosHundidos, textoDeResultado);
        }

        if (partidaTerminada) {
            finalizarPartida(true);
        }
        return partidaTerminada;
    }

    private boolean jugarTurnoDelServidor() throws IOException, ClassNotFoundException, InterruptedException {
        publicarEstado("Turno del servidor...");

        int disparosRecibidos = 0;
        boolean servidorFalloElDisparo = false;
        boolean partidaTerminada = false;

        while (disparosRecibidos < LogicaBarcos.DISPAROS_POR_TURNO && !servidorFalloElDisparo && !partidaTerminada) {
            Mensaje mensajeDisparo = cliente.esperarDisparoDelServidor();
            int filaRecibida = mensajeDisparo.getFila();
            int columnaRecibida = mensajeDisparo.getColumna();

            String resultado = logicaJugador.recibirDisparo(filaRecibida, columnaRecibida);

            if (LogicaBarcos.RESULTADO_REPETIDO.equals(resultado) || LogicaBarcos.RESULTADO_INVALIDO.equals(resultado)) {
                cliente.enviarResultado(resultado, false, null);
                continue;
            }

            boolean jugadorPerdio = logicaJugador.todosLosBarcosHundidos();
            LogicaBarcos.Barco barcoPropioHundido = logicaJugador.getBarcoHundidoEnUltimoDisparo();
            cliente.enviarResultado(resultado, jugadorPerdio, barcoPropioHundido);

            disparosRecibidos++;
            if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
                servidorFalloElDisparo = true;
            }
            if (jugadorPerdio) {
                partidaTerminada = true;
            }
            boolean elTurnoContinua = !servidorFalloElDisparo && !partidaTerminada
                    && disparosRecibidos < LogicaBarcos.DISPAROS_POR_TURNO;

            String nombreBarcoPropioHundido = (barcoPropioHundido != null) ? barcoPropioHundido.getNombre() : null;

            char[][] copiaDelTableroPropio = logicaJugador.copiarTableroPropio();
            String textoDeResultado = construirTextoDelDisparoDelServidor(filaRecibida, columnaRecibida, resultado,
                    nombreBarcoPropioHundido, partidaTerminada, elTurnoContinua);

            mostrarDisparoEnPantalla(panelDeFlotaPropia, filaRecibida, columnaRecibida, COLOR_RADAR_SERVIDOR,
                    resultado, copiaDelTableroPropio, logicaJugador.getBarcosPropios(), textoDeResultado);
        }

        if (partidaTerminada) {
            finalizarPartida(false);
        }
        return partidaTerminada;
    }

    private String construirTextoDelDisparoDelJugador(int fila, int columna, String resultado, String nombreBarcoHundido,
                                                      boolean partidaTerminada, boolean elTurnoContinua) {
        String nombreDeCasilla = LogicaBarcos.nombreDeCasilla(fila, columna);
        if (partidaTerminada) {
            return "¡Hundiste el " + nombreBarcoHundido + "! Toda la flota enemiga está hundida.";
        }
        String textoSiguiente = elTurnoContinua ? " Sigues disparando." : " Turno del servidor.";
        if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
            return "Fallaste en " + nombreDeCasilla + "." + textoSiguiente;
        }
        if (LogicaBarcos.RESULTADO_TOCADO.equals(resultado)) {
            return "¡Tocaste un barco en " + nombreDeCasilla + "!" + textoSiguiente;
        }
        return "¡Hundiste el " + nombreBarcoHundido + " enemigo!" + textoSiguiente;
    }

    private String construirTextoDelDisparoDelServidor(int fila, int columna, String resultado, String nombreBarcoHundido,
                                                       boolean partidaTerminada, boolean elTurnoContinua) {
        String nombreDeCasilla = LogicaBarcos.nombreDeCasilla(fila, columna);
        if (partidaTerminada) {
            return "El servidor hundió tu " + nombreBarcoHundido + ". Toda tu flota está hundida.";
        }
        String textoSiguiente = elTurnoContinua ? " Sigue disparando." : " Tu turno.";
        if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
            return "El servidor falló en " + nombreDeCasilla + "." + textoSiguiente;
        }
        if (LogicaBarcos.RESULTADO_TOCADO.equals(resultado)) {
            return "¡El servidor tocó uno de tus barcos en " + nombreDeCasilla + "!" + textoSiguiente;
        }
        return "¡El servidor hundió tu " + nombreBarcoHundido + "!" + textoSiguiente;
    }

    private void habilitarTableroEnemigo(final String textoDeEstado) {
        Platform.runLater(() -> {
            etiquetaDeEstado.setText(textoDeEstado);
            panelDeTableroEnemigo.setInteractivo(true);
        });
    }

    private void publicarEstado(final String textoDeEstado) {
        Platform.runLater(() -> etiquetaDeEstado.setText(textoDeEstado));
    }

    private void mostrarDisparoEnPantalla(final PanelTablero panel, final int fila, final int columna, final Color colorDelRadar,
                                          final String resultado, final char[][] copiaDelTablero,
                                          final List<LogicaBarcos.Barco> barcosAMostrar, final String textoDeEstado)
            throws InterruptedException {
        Platform.runLater(() -> {
            panel.mostrarRadar(fila, columna, colorDelRadar, () -> {
                panel.actualizar(copiaDelTablero, barcosAMostrar);
                agregarEfectoYSonido(panel, fila, columna, resultado);
                etiquetaDeEstado.setText(textoDeEstado);
                semaforoDeAnimacion.release();
            });
            Sonido.reproducir("radar");
        });
        semaforoDeAnimacion.acquire();
    }

    private void agregarEfectoYSonido(PanelTablero panel, int fila, int columna, String resultado) {
        int tipoDeEfecto;
        String nombreDelSonido;
        if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
            tipoDeEfecto = PanelTablero.EFECTO_SPLASH;
            nombreDelSonido = "splash";
        } else if (LogicaBarcos.RESULTADO_HUNDIDO.equals(resultado)) {
            tipoDeEfecto = PanelTablero.EFECTO_EXPLOSION_GRANDE;
            nombreDelSonido = "explosion_grande";
        } else {
            tipoDeEfecto = PanelTablero.EFECTO_EXPLOSION;
            nombreDelSonido = "explosion";
        }
        panel.agregarEfecto(tipoDeEfecto, fila, columna);
        Sonido.reproducir(nombreDelSonido);
    }

    private void alSeleccionarCasillaEnemiga(int fila, int columna) {
        if (faseActual != FASE_BATALLA) {
            return;
        }
        if (panelDeTableroEnemigo.getEstadoDeCasilla(fila, columna) != LogicaBarcos.CASILLA_AGUA) {
            etiquetaDeEstado.setText("Ya disparaste en esa casilla.");
            return;
        }
        panelDeTableroEnemigo.setInteractivo(false);
        filaSeleccionada = fila;
        columnaSeleccionada = columna;
        semaforoDeClic.release();
    }

    private void finalizarPartida(final boolean jugadorGano) throws InterruptedException {
        Platform.runLater(() -> {
            faseActual = FASE_TERMINADA;
            panelDeTableroEnemigo.setInteractivo(false);
            if (jugadorGano) {
                Sonido.reproducir("victoria");
            } else {
                Sonido.reproducir("derrota");
            }
        });

        Thread.sleep(PAUSA_ANTES_DEL_MENSAJE_FINAL_MILISEGUNDOS);

        final String mensajeFinal = jugadorGano
                ? "¡Ganaste! Hundiste toda la flota del servidor."
                : "Perdiste. El servidor hundió toda tu flota.";

        Platform.runLater(() -> {
            if (partidaDetenida) {
                return;
            }
            Alert alerta = new Alert(Alert.AlertType.INFORMATION);
            alerta.setTitle("Fin de la partida");
            alerta.setHeaderText(null);
            alerta.setContentText(mensajeFinal);
            alerta.showAndWait();

            detener();
            accionVolverAlMenu.run();
        });
    }

    private void mostrarErrorDeConexion(final String detalleDelError) {
        Platform.runLater(() -> {
            if (partidaDetenida) {
                return;
            }
            Alert alerta = new Alert(Alert.AlertType.ERROR);
            alerta.setTitle("Error de red");
            alerta.setHeaderText("Se perdió la conexión con el servidor.");
            alerta.setContentText(detalleDelError);
            alerta.showAndWait();

            detener();
            accionVolverAlMenu.run();
        });
    }

    @Override
    public void detener() {
        partidaDetenida = true;
        if (hiloDeRed != null) {
            hiloDeRed.interrupt();
        }
        cliente.cerrar();
        super.detener();
        panelDeColocacion.detener();
        panelDeFlotaPropia.detener();
        panelDeTableroEnemigo.detener();
    }
}