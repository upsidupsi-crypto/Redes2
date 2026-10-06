import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class PanelJuego extends PanelBase {

    private static final Color COLOR_RADAR_JUGADOR = new Color(60, 230, 110);
    private static final Color COLOR_RADAR_SERVIDOR = new Color(230, 70, 70);

    private static final int FASE_COLOCACION = 0;
    private static final int FASE_BATALLA = 1;
    private static final int FASE_TERMINADA = 2;

    private static final int PAUSA_ANTES_DEL_MENSAJE_FINAL_MILISEGUNDOS = 1400;

    private static final String PANTALLA_COLOCACION = "colocacion";
    private static final String PANTALLA_BATALLA = "batalla";

    private final Cliente cliente;
    private final Runnable accionVolverAlMenu;
    private LogicaBarcos logicaJugador;

    private final PanelTablero panelDeColocacion = new PanelTablero();
    private final PanelTablero panelDeFlotaPropia = new PanelTablero();
    private final PanelTablero panelDeTableroEnemigo = new PanelTablero();

    private final JLabel etiquetaDeEstado = new JLabel("Coloca tus barcos", SwingConstants.CENTER);
    private final JLabel etiquetaDeColocacion = new JLabel("", SwingConstants.CENTER);
    private final JButton botonComenzarBatalla = new JButton("Comenzar batalla");

    private final CardLayout administradorDePantallas = new CardLayout();
    private final JPanel panelCentral = new JPanel(administradorDePantallas);

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

        setLayout(new BorderLayout());

        add(construirBarraSuperior(), BorderLayout.NORTH);

        panelCentral.setOpaque(false);
        panelCentral.add(construirVistaDeColocacion(), PANTALLA_COLOCACION);
        panelCentral.add(construirVistaDeBatalla(), PANTALLA_BATALLA);
        add(panelCentral, BorderLayout.CENTER);

        panelDeColocacion.setOyenteDeCasilla(new PanelTablero.OyenteDeCasilla() {
            public void alSeleccionarCasilla(int fila, int columna) {
                alSeleccionarCasillaDeColocacion(fila, columna);
            }
        });
        panelDeTableroEnemigo.setOyenteDeCasilla(new PanelTablero.OyenteDeCasilla() {
            public void alSeleccionarCasilla(int fila, int columna) {
                alSeleccionarCasillaEnemiga(fila, columna);
            }
        });
        panelDeColocacion.setInteractivo(true);
        panelDeFlotaPropia.setInteractivo(false);
        panelDeTableroEnemigo.setInteractivo(false);

        actualizarTextoDeColocacion();
        actualizarTablerosDeColocacion();
    }

    private JPanel construirBarraSuperior() {
        JPanel barraSuperior = new JPanel(new BorderLayout());
        barraSuperior.setOpaque(false);
        barraSuperior.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        etiquetaDeEstado.setFont(new Font("SansSerif", Font.BOLD, 20));
        etiquetaDeEstado.setForeground(Color.WHITE);

        JButton botonMenu = new JButton("\u2190 Menú");
        botonMenu.setFocusPainted(false);
        botonMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evento) {
                detener();
                accionVolverAlMenu.run();
            }
        });

        barraSuperior.add(botonMenu, BorderLayout.WEST);
        barraSuperior.add(etiquetaDeEstado, BorderLayout.CENTER);
        return barraSuperior;
    }

    private JPanel construirVistaDeColocacion() {
        JPanel vistaDeColocacion = new JPanel(new BorderLayout());
        vistaDeColocacion.setOpaque(false);
        vistaDeColocacion.add(panelDeColocacion, BorderLayout.CENTER);

        JPanel panelDeControles = new JPanel();
        panelDeControles.setOpaque(false);
        panelDeControles.setLayout(new BoxLayout(panelDeControles, BoxLayout.Y_AXIS));

        etiquetaDeColocacion.setForeground(Color.WHITE);
        etiquetaDeColocacion.setFont(new Font("SansSerif", Font.PLAIN, 15));
        etiquetaDeColocacion.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel filaDeBotones = new JPanel();
        filaDeBotones.setOpaque(false);

        JButton botonGirar = new JButton("Girar orientación");
        botonGirar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evento) {
                orientacionHorizontal = !orientacionHorizontal;
                actualizarTextoDeColocacion();
            }
        });

        JButton botonColocacionAleatoria = new JButton("Colocación aleatoria");
        botonColocacionAleatoria.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evento) {
                logicaJugador = new LogicaBarcos();
                logicaJugador.colocarFlotaAleatoria();
                indiceBarcoPendiente = LogicaBarcos.LONGITUDES_BARCOS.length;
                Sonido.reproducir("colocar");
                actualizarTablerosDeColocacion();
                actualizarTextoDeColocacion();
                botonComenzarBatalla.setEnabled(true);
            }
        });

        JButton botonReiniciarColocacion = new JButton("Reiniciar colocación");
        botonReiniciarColocacion.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evento) {
                logicaJugador = new LogicaBarcos();
                indiceBarcoPendiente = 0;
                Sonido.reproducir("click");
                actualizarTablerosDeColocacion();
                actualizarTextoDeColocacion();
                botonComenzarBatalla.setEnabled(false);
            }
        });

        botonComenzarBatalla.setEnabled(false);
        botonComenzarBatalla.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evento) {
                iniciarBatalla();
            }
        });

        filaDeBotones.add(botonGirar);
        filaDeBotones.add(botonColocacionAleatoria);
        filaDeBotones.add(botonReiniciarColocacion);
        filaDeBotones.add(botonComenzarBatalla);

        panelDeControles.add(etiquetaDeColocacion);
        panelDeControles.add(Box.createVerticalStrut(8));
        panelDeControles.add(filaDeBotones);

        vistaDeColocacion.add(panelDeControles, BorderLayout.SOUTH);
        return vistaDeColocacion;
    }

    private JPanel construirVistaDeBatalla() {
        JPanel vistaDeBatalla = new JPanel(new GridLayout(1, 2, 10, 0));
        vistaDeBatalla.setOpaque(false);
        vistaDeBatalla.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        vistaDeBatalla.add(envolverConTitulo(panelDeFlotaPropia, "Tu flota"));
        vistaDeBatalla.add(envolverConTitulo(panelDeTableroEnemigo, "Tablero enemigo"));
        return vistaDeBatalla;
    }

    private JPanel envolverConTitulo(JComponent componente, String titulo) {
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setOpaque(false);
        JLabel etiquetaDeTitulo = new JLabel(titulo, SwingConstants.CENTER);
        etiquetaDeTitulo.setForeground(Color.WHITE);
        etiquetaDeTitulo.setFont(new Font("SansSerif", Font.BOLD, 16));
        contenedor.add(etiquetaDeTitulo, BorderLayout.NORTH);
        contenedor.add(componente, BorderLayout.CENTER);
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
        String textoDeOrientacion;
        if (orientacionHorizontal) {
            textoDeOrientacion = "Horizontal";
        } else {
            textoDeOrientacion = "Vertical";
        }
        etiquetaDeColocacion.setText("Coloca: " + LogicaBarcos.NOMBRES_BARCOS[indiceBarcoPendiente]
                + " (" + LogicaBarcos.LONGITUDES_BARCOS[indiceBarcoPendiente] + " casillas) - " + textoDeOrientacion);
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
                botonComenzarBatalla.setEnabled(true);
            }
        } else {
            etiquetaDeColocacion.setText("Posición inválida: el barco se sale del tablero o se encima con otro. Intenta otra casilla.");
        }
    }

    private void iniciarBatalla() {
        faseActual = FASE_BATALLA;
        botonComenzarBatalla.setEnabled(false);
        panelDeColocacion.setInteractivo(false);

        panelDeColocacion.actualizar(logicaJugador.copiarTableroPropio(), logicaJugador.getBarcosPropios());
        panelDeFlotaPropia.actualizar(logicaJugador.copiarTableroPropio(), logicaJugador.getBarcosPropios());
        panelDeTableroEnemigo.actualizar(logicaJugador.copiarTableroDeTiro(), new ArrayList<LogicaBarcos.Barco>());

        etiquetaDeEstado.setText("Esperando al servidor...");
        administradorDePantallas.show(panelCentral, PANTALLA_BATALLA);

        hiloDeRed = new Thread(new Runnable() {
            public void run() {
                ejecutarPartida();
            }
        });
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
                throw new IOException("El servidor respondio con un resultado invalido: " + resultado);
            }

            logicaJugador.registrarResultadoDisparo(filaDisparada, columnaDisparada, resultado);
            if (LogicaBarcos.RESULTADO_HUNDIDO.equals(resultado)) {
                if (!mensajeResultado.tieneBarcoHundido()) {
                    throw new IOException("El servidor no indico cual barco se hundio");
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

            String nombreBarcoPropioHundido = null;
            if (barcoPropioHundido != null) {
                nombreBarcoPropioHundido = barcoPropioHundido.getNombre();
            }

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
        String textoDelSiguienteTurno;
        if (elTurnoContinua) {
            textoDelSiguienteTurno = " Sigues disparando.";
        } else {
            textoDelSiguienteTurno = " Turno del servidor.";
        }
        if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
            return "Fallaste en " + nombreDeCasilla + "." + textoDelSiguienteTurno;
        }
        if (LogicaBarcos.RESULTADO_TOCADO.equals(resultado)) {
            return "¡Tocaste un barco en " + nombreDeCasilla + "!" + textoDelSiguienteTurno;
        }
        return "¡Hundiste el " + nombreBarcoHundido + " enemigo!" + textoDelSiguienteTurno;
    }

    private String construirTextoDelDisparoDelServidor(int fila, int columna, String resultado, String nombreBarcoHundido,
                                                       boolean partidaTerminada, boolean elTurnoContinua) {
        String nombreDeCasilla = LogicaBarcos.nombreDeCasilla(fila, columna);
        if (partidaTerminada) {
            return "El servidor hundió tu " + nombreBarcoHundido + ". Toda tu flota está hundida.";
        }
        String textoDelSiguienteTurno;
        if (elTurnoContinua) {
            textoDelSiguienteTurno = " Sigue disparando.";
        } else {
            textoDelSiguienteTurno = " Tu turno.";
        }
        if (LogicaBarcos.RESULTADO_AGUA.equals(resultado)) {
            return "El servidor falló en " + nombreDeCasilla + "." + textoDelSiguienteTurno;
        }
        if (LogicaBarcos.RESULTADO_TOCADO.equals(resultado)) {
            return "¡El servidor tocó uno de tus barcos en " + nombreDeCasilla + "!" + textoDelSiguienteTurno;
        }
        return "¡El servidor hundió tu " + nombreBarcoHundido + "!" + textoDelSiguienteTurno;
    }

    private void habilitarTableroEnemigo(final String textoDeEstado) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                etiquetaDeEstado.setText(textoDeEstado);
                panelDeTableroEnemigo.setInteractivo(true);
            }
        });
    }

    private void publicarEstado(final String textoDeEstado) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                etiquetaDeEstado.setText(textoDeEstado);
            }
        });
    }

    private void mostrarDisparoEnPantalla(final PanelTablero panel, final int fila, final int columna, final Color colorDelRadar,
                                          final String resultado, final char[][] copiaDelTablero,
                                          final List<LogicaBarcos.Barco> barcosAMostrar, final String textoDeEstado)
            throws InterruptedException {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                panel.mostrarRadar(fila, columna, colorDelRadar, new Runnable() {
                    public void run() {
                        panel.actualizar(copiaDelTablero, barcosAMostrar);
                        agregarEfectoYSonido(panel, fila, columna, resultado);
                        etiquetaDeEstado.setText(textoDeEstado);
                        semaforoDeAnimacion.release();
                    }
                });
                Sonido.reproducir("radar");
            }
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
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                faseActual = FASE_TERMINADA;
                panelDeTableroEnemigo.setInteractivo(false);
                if (jugadorGano) {
                    Sonido.reproducir("victoria");
                } else {
                    Sonido.reproducir("derrota");
                }
            }
        });

        Thread.sleep(PAUSA_ANTES_DEL_MENSAJE_FINAL_MILISEGUNDOS);

        final String mensajeFinal;
        if (jugadorGano) {
            mensajeFinal = "¡Ganaste! Hundiste toda la flota del servidor.";
        } else {
            mensajeFinal = "Perdiste. El servidor hundió toda tu flota.";
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (partidaDetenida) {
                    return;
                }
                JOptionPane.showMessageDialog(PanelJuego.this, mensajeFinal, "Fin de la partida", JOptionPane.INFORMATION_MESSAGE);
                detener();
                accionVolverAlMenu.run();
            }
        });
    }

    private void mostrarErrorDeConexion(final String detalleDelError) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (partidaDetenida) {
                    return;
                }
                JOptionPane.showMessageDialog(PanelJuego.this,
                        "Se perdió la conexión con el servidor.\n" + detalleDelError,
                        "Error de red", JOptionPane.ERROR_MESSAGE);
                detener();
                accionVolverAlMenu.run();
            }
        });
    }

    protected void dibujarContenido(Graphics2D graficos) {
    }

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