import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Director" del juego: conecta la lógica (LogicaBarcos) con la vista
 * (PanelTablero). Maneja las tres fases: colocación, batalla y fin.
 */
public class PanelJuego extends PanelBase {

    private static final Color RADAR_JUGADOR = new Color(60, 230, 110);
    private static final Color RADAR_PC = new Color(230, 70, 70);

    private enum Fase { COLOCACION, BATALLA, TERMINADO }

    private final Runnable alVolverMenu;
    private final Random random = new Random();

    private LogicaBarcos jugador = new LogicaBarcos();
    private final LogicaBarcos pc = new LogicaBarcos();

    // Un tablero SOLO para colocar, y otro para "Tu flota" durante la batalla.
    // (Un componente Swing solo puede tener un padre: antes se reutilizaba el
    // mismo panel en las dos vistas y el tablero desaparecía de la colocación.)
    private final PanelTablero panelColocacion = new PanelTablero();
    private final PanelTablero panelPropio = new PanelTablero();
    private final PanelTablero panelEnemigo = new PanelTablero();

    private final JLabel etiquetaEstado = new JLabel("Coloca tus barcos", SwingConstants.CENTER);
    private final JLabel etiquetaColocacion = new JLabel("", SwingConstants.CENTER);
    private final JButton botonComenzar = new JButton("Comenzar batalla");

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel panelCentral = new JPanel(cardLayout);

    private final String[] tipos = {"Submarino", "Acorazado", "Crucero", "Crucero", "Destructor", "Destructor", "Destructor"};
    private final int[] longitudes = {5, 4, 3, 3, 2, 2, 2};
    private int indicePendiente = 0;
    private boolean orientacionHorizontal = true;

    private Fase faseActual = Fase.COLOCACION;
    private boolean turnoJugador;
    private int disparosTurno = 0;

    // true cuando la partida se abandona o termina; los Timer pendientes
    // ya no deben hacer nada (antes podían disparar sonidos o mostrar el
    // diálogo de fin después de volver al menú).
    private boolean detenido = false;

    public PanelJuego(Runnable alVolverMenu) {
        this.alVolverMenu = alVolverMenu;
        setLayout(new BorderLayout());

        add(construirBarraSuperior(), BorderLayout.NORTH);

        panelCentral.setOpaque(false);
        panelCentral.add(construirVistaColocacion(), "colocacion");
        panelCentral.add(construirVistaBatalla(), "batalla");
        add(panelCentral, BorderLayout.CENTER);

        panelColocacion.setOyente((fila, col) -> intentarColocar(fila, col));
        panelEnemigo.setOyente((fila, col) -> intentarDisparoJugador(fila, col));
        panelColocacion.setInteractivo(true);
        panelPropio.setInteractivo(false);
        panelEnemigo.setInteractivo(false);

        actualizarEtiquetaColocacion();
        actualizarVistas();
    }

    // ---------- Construcción de la interfaz ----------

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
            jugador.colocarBarcosAleatorio();
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

    // ---------- Utilidad: ejecutar algo después de un retraso ----------

    /** Como un Timer de un solo disparo, pero no ejecuta nada si la partida ya se detuvo. */
    private void retrasar(int milisegundos, Runnable accion) {
        Timer t = new Timer(milisegundos, e -> {
            if (!detenido) accion.run();
        });
        t.setRepeats(false);
        t.start();
    }

    // ---------- Colocación de barcos ----------

    private void actualizarEtiquetaColocacion() {
        if (indicePendiente >= tipos.length) {
            etiquetaColocacion.setText("Flota completa. Pulsa \"Comenzar batalla\".");
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

    // ---------- Batalla ----------

    private void iniciarBatalla() {
        pc.colocarBarcosAleatorio();
        faseActual = Fase.BATALLA;
        cardLayout.show(panelCentral, "batalla");
        panelColocacion.setInteractivo(false);
        disparosTurno = 0;
        turnoJugador = random.nextBoolean();
        actualizarVistas();

        if (turnoJugador) {
            etiquetaEstado.setText("Tu turno: dispara al tablero enemigo");
            panelEnemigo.setInteractivo(true);
        } else {
            etiquetaEstado.setText("Turno de la PC...");
            panelEnemigo.setInteractivo(false);
            retrasar(900, this::disparoPC);
        }
    }

    private void intentarDisparoJugador(int fila, int col) {
        if (!turnoJugador || faseActual != Fase.BATALLA) return;
        if (jugador.getTableroTiro()[fila][col] != LogicaBarcos.AGUA) {
            etiquetaEstado.setText("Ya disparaste en esa casilla.");
            return;
        }
        panelEnemigo.setInteractivo(false);
        panelEnemigo.mostrarRadar(fila, col, RADAR_JUGADOR, () -> resolverDisparoJugador(fila, col));
        Sonido.reproducir("radar");
    }

    private void resolverDisparoJugador(int fila, int col) {
        String resultado = pc.recibirDisparo(fila, col);
        boolean pcPerdio = pc.todosHundidos();
        jugador.registrarResultadoTiro(fila, col, resultado);
        actualizarVistas();

        agregarEfectoYSonido(panelEnemigo, fila, col, resultado);
        etiquetaEstado.setText(mensajeResultado(resultado, true));

        if (pcPerdio) {
            terminarJuego(true);
            return;
        }

        disparosTurno++;
        if (resultado.equals("AGUA") || disparosTurno >= 3) {
            cambiarTurnoAPC();
        } else {
            retrasar(650, () -> panelEnemigo.setInteractivo(true));
        }
    }

    private void cambiarTurnoAPC() {
        turnoJugador = false;
        disparosTurno = 0;
        panelEnemigo.setInteractivo(false);
        etiquetaEstado.setText("Turno de la PC...");
        retrasar(900, this::disparoPC);
    }

    private void disparoPC() {
        int[] coord = elegirDisparoPC();
        panelPropio.mostrarRadar(coord[0], coord[1], RADAR_PC, () -> resolverDisparoPC(coord[0], coord[1]));
        Sonido.reproducir("radar");
    }

    private void resolverDisparoPC(int fila, int col) {
        String resultado = jugador.recibirDisparo(fila, col);
        boolean jugadorPerdio = jugador.todosHundidos();
        pc.registrarResultadoTiro(fila, col, resultado);
        actualizarVistas();

        agregarEfectoYSonido(panelPropio, fila, col, resultado);
        etiquetaEstado.setText(mensajeResultado(resultado, false));

        if (jugadorPerdio) {
            terminarJuego(false);
            return;
        }

        disparosTurno++;
        if (resultado.equals("AGUA") || disparosTurno >= 3) {
            cambiarTurnoAJugador();
        } else {
            retrasar(900, this::disparoPC);
        }
    }

    private void cambiarTurnoAJugador() {
        turnoJugador = true;
        disparosTurno = 0;
        etiquetaEstado.setText("Tu turno: dispara al tablero enemigo");
        panelEnemigo.setInteractivo(true);
    }

    private int[] elegirDisparoPC() {
        char[][] tablero = jugador.getTableroPropio();
        int fila, col;
        do {
            fila = random.nextInt(LogicaBarcos.TAM);
            col = random.nextInt(LogicaBarcos.TAM);
        } while (tablero[fila][col] == LogicaBarcos.TOCADO
                || tablero[fila][col] == LogicaBarcos.FALLO
                || tablero[fila][col] == LogicaBarcos.HUNDIDO);
        return new int[]{fila, col};
    }

    private void agregarEfectoYSonido(PanelTablero panel, int fila, int col, String resultado) {
        PanelTablero.TipoEfecto tipo;
        String sonido;
        if (resultado.equals("AGUA")) {
            tipo = PanelTablero.TipoEfecto.SPLASH;
            sonido = "splash";
        } else if (resultado.equals("HUNDIDO")) {
            tipo = PanelTablero.TipoEfecto.EXPLOSION_GRANDE;
            sonido = "explosion_grande";
        } else {
            tipo = PanelTablero.TipoEfecto.EXPLOSION;
            sonido = "explosion";
        }
        panel.agregarEfecto(tipo, fila, col);
        Sonido.reproducir(sonido);
    }

    private String mensajeResultado(String resultado, boolean disparoDelJugador) {
        if (resultado.equals("AGUA")) {
            return disparoDelJugador ? "Fallaste. Turno de la PC." : "La PC falló. Tu turno.";
        } else if (resultado.equals("TOCADO")) {
            return disparoDelJugador ? "¡Le diste a un barco! Sigues disparando." : "¡La PC te dio! Sigue disparando.";
        } else {
            return disparoDelJugador ? "¡Hundiste un barco enemigo!" : "¡La PC hundió uno de tus barcos!";
        }
    }

    private void terminarJuego(boolean jugadorGano) {
        faseActual = Fase.TERMINADO;
        panelEnemigo.setInteractivo(false);
        Sonido.reproducir(jugadorGano ? "victoria" : "derrota");
        String mensaje = jugadorGano
                ? "¡Ganaste! Hundiste toda la flota enemiga."
                : "Perdiste. La PC hundió toda tu flota.";
        // pequeña pausa para que se vea la última explosión antes del diálogo
        retrasar(1400, () -> {
            JOptionPane.showMessageDialog(this, mensaje, "Fin de la partida", JOptionPane.INFORMATION_MESSAGE);
            detener();
            alVolverMenu.run();
        });
    }

    private void actualizarVistas() {
        List<LogicaBarcos.Barco> hundidosPc = new ArrayList<>();
        for (LogicaBarcos.Barco b : pc.getMisBarcos()) {
            if (b.estaHundido()) hundidosPc.add(b);
        }
        panelEnemigo.actualizar(jugador.getTableroTiro(), hundidosPc);
        panelPropio.actualizar(jugador.getTableroPropio(), jugador.getMisBarcos());
        panelColocacion.actualizar(jugador.getTableroPropio(), jugador.getMisBarcos());
    }

    @Override
    protected void dibujarContenido(Graphics2D g2) {
        // el fondo de agua ya lo pinta PanelBase; el resto son componentes Swing
    }

    /** Detiene todos los temporizadores de esta partida (tableros, agua y retrasos pendientes). */
    @Override
    public void detener() {
        detenido = true;
        super.detener();
        panelColocacion.detener();
        panelPropio.detener();
        panelEnemigo.detener();
    }
}
