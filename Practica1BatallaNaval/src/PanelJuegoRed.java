import javax.swing.*;
import java.awt.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class PanelJuegoRed extends PanelBase {

    private static final Color RADAR_JUGADOR = new Color(60, 230, 110);
    private static final Color RADAR_RIVAL = new Color(230, 70, 70);

    private enum Fase { COLOCACION, BATALLA, TERMINADO }

    private final Cliente cliente;
    private final Runnable alVolverMenu;

    private LogicaBarcos jugador = new LogicaBarcos();

    private final PanelTablero panelColocacion = new PanelTablero();
    private final PanelTablero panelPropio = new PanelTablero();
    private final PanelTablero panelEnemigo = new PanelTablero();

    private final JLabel etiquetaEstado = new JLabel("Coloca tus barcos", SwingConstants.CENTER);
    private final JLabel etiquetaColocacion = new JLabel("", SwingConstants.CENTER);
    private final JButton botonComenzar = new JButton("Enviar flota y comenzar");

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel panelCentral = new JPanel(cardLayout);

    private final String[] tipos = {"Submarino", "Acorazado", "Crucero", "Crucero", "Destructor", "Destructor", "Destructor"};
    private final int[] longitudes = {5, 4, 3, 3, 2, 2, 2};
    private int indicePendiente = 0;
    private boolean orientacionHorizontal = true;

    private Fase faseActual = Fase.COLOCACION;

    private final BlockingQueue<int[]> colaClicsUsuario = new LinkedBlockingQueue<>();
    private Thread hiloRed;

    private volatile boolean detenido = false;

    public PanelJuegoRed(Cliente cliente, Runnable alVolverMenu) {
        this.cliente = cliente;
        this.alVolverMenu = alVolverMenu;
        setLayout(new BorderLayout());

        add(construirBarraSuperior(), BorderLayout.NORTH);

        panelCentral.setOpaque(false);
        panelCentral.add(construirVistaColocacion(), "colocacion");
        panelCentral.add(construirVistaBatalla(), "batalla");
        add(panelCentral, BorderLayout.CENTER);

        panelColocacion.setOyenteDeCasilla((fila, col) -> intentarColocar(fila, col));
        panelEnemigo.setOyenteDeCasilla((fila, col) -> onClicEnemigo(fila, col));
        panelColocacion.setInteractivo(true);
        panelPropio.setInteractivo(false);
        panelEnemigo.setInteractivo(false);

        actualizarEtiquetaColocacion();
        actualizarVistas();
    }

    private JPanel construirBarraSuperior() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);
        barra.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        etiquetaEstado.setFont(new Font("SansSerif", Font.BOLD, 20));
        etiquetaEstado.setForeground(Color.WHITE);

        JButton botonMenu = new JButton("\u2190 Menú");
        botonMenu.setFocusPainted(false);
        botonMenu.addActionListener(e -> {
            detener();
            alVolverMenu.run();
        });

        barra.add(botonMenu, BorderLayout.WEST);
        barra.add(etiquetaEstado, BorderLayout.CENTER);
        return barra;
    }

    private JPanel construirVistaColocacion() {
        JPanel vista = new JPanel(new BorderLayout());
        vista.setOpaque(false);
        vista.add(panelColocacion, BorderLayout.CENTER);

        JPanel controles = new JPanel();
        controles.setOpaque(false);
        controles.setLayout(new BoxLayout(controles, BoxLayout.Y_AXIS));

        etiquetaColocacion.setForeground(Color.WHITE);
        etiquetaColocacion.setFont(new Font("SansSerif", Font.PLAIN, 15));
        etiquetaColocacion.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel fila = new JPanel();
        fila.setOpaque(false);

        JButton botonGirar = new JButton("Girar orientación");
        botonGirar.addActionListener(e -> {
            orientacionHorizontal = !orientacionHorizontal;
            actualizarEtiquetaColocacion();
        });

        JButton botonAleatorio = new JButton("Colocación aleatoria");
        botonAleatorio.addActionListener(e -> {
            jugador = new LogicaBarcos();
            jugador.colocarFlotaAleatoria();
            indicePendiente = tipos.length;
            Sonido.reproducir("colocar");
            actualizarVistas();
            actualizarEtiquetaColocacion();
            botonComenzar.setEnabled(true);
        });

        botonComenzar.setEnabled(false);
        botonComenzar.addActionListener(e -> iniciarBatalla());

        fila.add(botonGirar);
        fila.add(botonAleatorio);
        fila.add(botonComenzar);

        controles.add(etiquetaColocacion);
        controles.add(Box.createVerticalStrut(8));
        controles.add(fila);

        vista.add(controles, BorderLayout.SOUTH);
        return vista;
    }

    private JPanel construirVistaBatalla() {
        JPanel vista = new JPanel(new GridLayout(1, 2, 10, 0));
        vista.setOpaque(false);
        vista.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        vista.add(envolverConTitulo(panelPropio, "Tu flota"));
        vista.add(envolverConTitulo(panelEnemigo, "Tablero enemigo"));
        return vista;
    }

    private JPanel envolverConTitulo(JComponent comp, String titulo) {
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setOpaque(false);
        JLabel etiqueta = new JLabel(titulo, SwingConstants.CENTER);
        etiqueta.setForeground(Color.WHITE);
        etiqueta.setFont(new Font("SansSerif", Font.BOLD, 16));
        contenedor.add(etiqueta, BorderLayout.NORTH);
        contenedor.add(comp, BorderLayout.CENTER);
        return contenedor;
    }

    private void retrasar(int milisegundos, Runnable accion) {
        Timer t = new Timer(milisegundos, e -> {
            if (!detenido) accion.run();
        });
        t.setRepeats(false);
        t.start();
    }

    private void actualizarEtiquetaColocacion() {
        if (indicePendiente >= tipos.length) {
            etiquetaColocacion.setText("Flota completa. Pulsa \"Enviar flota y comenzar\".");
            return;
        }
        String orientacion = orientacionHorizontal ? "Horizontal" : "Vertical";
        etiquetaColocacion.setText("Coloca: " + tipos[indicePendiente] + " (" + longitudes[indicePendiente] + " casillas) - " + orientacion);
    }

    private void intentarColocar(int fila, int col) {
        if (faseActual != Fase.COLOCACION || indicePendiente >= tipos.length) return;
        boolean ok = jugador.colocarBarco(tipos[indicePendiente], longitudes[indicePendiente], fila, col, orientacionHorizontal);
        if (ok) {
            Sonido.reproducir("colocar");
            indicePendiente++;
            actualizarVistas();
            actualizarEtiquetaColocacion();
            if (indicePendiente >= tipos.length) {
                botonComenzar.setEnabled(true);
            }
        } else {
            etiquetaColocacion.setText("Posición inválida, intenta otra casilla.");
        }
    }

    private void iniciarBatalla() {
        faseActual = Fase.BATALLA;
        cardLayout.show(panelCentral, "batalla");
        panelColocacion.setInteractivo(false);
        etiquetaEstado.setText("Enviando tu flota al servidor...");
        actualizarVistas();

        hiloRed = new Thread(this::bucleRed, "hilo-red-cliente");
        hiloRed.setDaemon(true);
        hiloRed.start();
    }


    private void bucleRed() {
        try {
            boolean turnoCliente = cliente.enviarListoYEsperarTurno();
            publicarEstado(turnoCliente ? "Tu turno: dispara al tablero enemigo" : "Turno del rival...");

            boolean fin = false;
            while (!fin && !detenido) {
                fin = turnoCliente ? turnoDelCliente() : turnoDelServidor();
                turnoCliente = !turnoCliente;
            }
        } catch (Exception e) {
            if (!detenido) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Se perdió la conexión con el servidor.\n" + e.getMessage(),
                            "Error de red", JOptionPane.ERROR_MESSAGE);
                    detener();
                    alVolverMenu.run();
                });
            }
        }
    }

    private boolean turnoDelCliente() throws Exception {
        int disparos = 0;
        boolean fallo = false;
        boolean fin = false;

        while (disparos < 3 && !fallo && !fin) {
            habilitarClicEnemigo();
            int[] casilla = colaClicsUsuario.take();

            Mensaje respuesta = cliente.disparar(casilla[0], casilla[1]);
            String resultado = respuesta.getTexto();
            if ("REPETIDO".equals(resultado)) continue;

            jugador.registrarResultadoDisparo(casilla[0], casilla[1], resultado);
            boolean rivalPerdio = respuesta.isFinDeJuego();

            mostrarDisparoPropioEnGUI(casilla[0], casilla[1], resultado, rivalPerdio);

            if (rivalPerdio) { fin = true; break; }
            if ("AGUA".equals(resultado)) fallo = true;
            disparos++;
        }

        if (!fin) publicarEstado("Turno del rival...");
        return fin;
    }

    private boolean turnoDelServidor() throws Exception {
        int disparos = 0;
        boolean fallo = false;
        boolean fin = false;

        while (disparos < 3 && !fallo && !fin) {
            Mensaje disparo = cliente.esperarDisparoDelServidor();
            int fila = disparo.getFila();
            int col = disparo.getColumna();

            String resultado = jugador.recibirDisparo(fila, col);
            boolean yoPerdi = jugador.todosLosBarcosHundidos();
            cliente.enviarResultado(resultado, yoPerdi, null);

            mostrarDisparoEnemigoEnGUI(fila, col, resultado, yoPerdi);

            if (yoPerdi) { fin = true; break; }
            if ("AGUA".equals(resultado)) fallo = true;
            disparos++;
        }

        if (!fin) publicarEstado("Tu turno: dispara al tablero enemigo");
        return fin;
    }

    private void habilitarClicEnemigo() {
        SwingUtilities.invokeLater(() -> panelEnemigo.setInteractivo(true));
    }

    private void publicarEstado(String texto) {
        SwingUtilities.invokeLater(() -> etiquetaEstado.setText(texto));
    }

    private void mostrarDisparoPropioEnGUI(int fila, int col, String resultado, boolean finDeJuego) throws InterruptedException {
        CountDownLatch listo = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            actualizarVistas();
            panelEnemigo.mostrarRadar(fila, col, RADAR_JUGADOR, () -> {
                agregarEfectoYSonido(panelEnemigo, fila, col, resultado);
                etiquetaEstado.setText(mensajeResultado(resultado, true));
                if (finDeJuego) terminarJuego(true);
                listo.countDown();
            });
            Sonido.reproducir("radar");
        });
        listo.await();
    }

    private void mostrarDisparoEnemigoEnGUI(int fila, int col, String resultado, boolean finDeJuego) throws InterruptedException {
        CountDownLatch listo = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            actualizarVistas();
            panelPropio.mostrarRadar(fila, col, RADAR_RIVAL, () -> {
                agregarEfectoYSonido(panelPropio, fila, col, resultado);
                etiquetaEstado.setText(mensajeResultado(resultado, false));
                if (finDeJuego) terminarJuego(false);
                listo.countDown();
            });
            Sonido.reproducir("radar");
        });
        listo.await();
    }

    private void agregarEfectoYSonido(PanelTablero panel, int fila, int col, String resultado) {
        int tipo;
        String sonido;
        if ("AGUA".equals(resultado)) {
            tipo = PanelTablero.EFECTO_SPLASH;
            sonido = "splash";
        } else if ("HUNDIDO".equals(resultado)) {
            tipo = PanelTablero.EFECTO_EXPLOSION_GRANDE;
            sonido = "explosion_grande";
        } else {
            tipo = PanelTablero.EFECTO_EXPLOSION;
            sonido = "explosion";
        }
        panel.agregarEfecto(tipo, fila, col);
        Sonido.reproducir(sonido);
    }

    private String mensajeResultado(String resultado, boolean disparoPropio) {
        if ("AGUA".equals(resultado)) {
            return disparoPropio ? "Fallaste. Turno del rival." : "El rival falló. Tu turno.";
        } else if ("TOCADO".equals(resultado)) {
            return disparoPropio ? "¡Le diste a un barco! Sigues disparando." : "¡El rival te dio! Sigue disparando.";
        } else {
            return disparoPropio ? "¡Hundiste un barco rival!" : "¡El rival hundió uno de tus barcos!";
        }
    }

    private void terminarJuego(boolean jugadorGano) {
        faseActual = Fase.TERMINADO;
        panelEnemigo.setInteractivo(false);
        Sonido.reproducir(jugadorGano ? "victoria" : "derrota");
        String mensaje = jugadorGano
                ? "¡Ganaste! Hundiste toda la flota rival."
                : "Perdiste. El rival hundió toda tu flota.";
        retrasar(1400, () -> {
            JOptionPane.showMessageDialog(this, mensaje, "Fin de la partida", JOptionPane.INFORMATION_MESSAGE);
            detener();
            alVolverMenu.run();
        });
    }

    private void onClicEnemigo(int fila, int col) {
        if (jugador.getTableroDeTiro()[fila][col] != LogicaBarcos.CASILLA_AGUA) {
            etiquetaEstado.setText("Ya disparaste en esa casilla.");
            return;
        }
        panelEnemigo.setInteractivo(false);
        colaClicsUsuario.offer(new int[]{fila, col});
    }

    private void actualizarVistas() {
        panelEnemigo.actualizar(jugador.copiarTableroDeTiro(), java.util.Collections.emptyList());
        panelPropio.actualizar(jugador.getTableroPropio(), jugador.getBarcosPropios());
        panelColocacion.actualizar(jugador.getTableroPropio(), jugador.getBarcosPropios());
    }

    @Override
    protected void dibujarContenido(Graphics2D g2) {
    }

    @Override
    public void detener() {
        detenido = true;
        if (hiloRed != null) hiloRed.interrupt();
        cliente.cerrar();
        super.detener();
        panelColocacion.detener();
        panelPropio.detener();
        panelEnemigo.detener();
    }
}
