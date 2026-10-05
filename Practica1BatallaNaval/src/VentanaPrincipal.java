import javax.swing.*;
import java.awt.*;

/**
 * Ventana principal y punto de entrada del programa (antes Main.java).
 * Contiene un CardLayout con dos pantallas: el menú (PanelMenu, clase
 * anidada aquí abajo) y el juego (PanelJuego).
 *
 * Ejecutar con:  java VentanaPrincipal
 */
public class VentanaPrincipal extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal ventana = new VentanaPrincipal();
            ventana.setVisible(true);
        });
    }

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contenedor = new JPanel(cardLayout);
    private PanelBase juegoActual;

    public VentanaPrincipal() {
        super("Batalla Naval");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 720);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);

        // se cargan todos los .wav una sola vez, al abrir el programa
        Sonido.precargar("click", "colocar", "radar", "splash",
                "explosion", "explosion_grande", "victoria", "derrota");

        mostrarMenu();

        setContentPane(contenedor);
    }

    private void mostrarMenu() {
        PanelMenu menu = new PanelMenu(this::mostrarJuego, this::mostrarJuegoRed);
        contenedor.add(menu, "menu");
        cardLayout.show(contenedor, "menu");
    }

    private void mostrarJuego() {
        // se crea un PanelJuego nuevo cada vez, así cada partida empieza limpia;
        // pero antes hay que detener y quitar el anterior para no dejar
        // temporizadores (Timer) corriendo en segundo plano para siempre.
        reemplazarJuegoActual(new PanelJuego(this::volverAlMenu));
    }

    /**
     * Pide host/puerto/nombre, se conecta a Servidor.java en un hilo aparte
     * (para no congelar la ventana mientras se espera la conexión) y, si
     * todo sale bien, abre PanelJuegoRed. Servidor.java debe estar corriendo
     * antes de pulsar "Jugar en red".
     */
    private void mostrarJuegoRed() {
        String host = JOptionPane.showInputDialog(this, "IP o host del servidor:", "localhost");
        if (host == null || host.isBlank()) return;

        String puertoTexto = JOptionPane.showInputDialog(this, "Puerto del servidor:", "5000");
        if (puertoTexto == null || puertoTexto.isBlank()) return;

        String nombre = JOptionPane.showInputDialog(this, "Tu nombre de jugador:", "Jugador");
        if (nombre == null || nombre.isBlank()) nombre = "Jugador";

        int puerto;
        try {
            puerto = Integer.parseInt(puertoTexto.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "El puerto debe ser un número.", "Puerto inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String hostFinal = host.trim();
        String nombreFinal = nombre;

        new Thread(() -> {
            try {
                Cliente cliente = new Cliente(hostFinal, puerto);
                cliente.enviarNombreYEsperarInicio(nombreFinal);
                SwingUtilities.invokeLater(() -> reemplazarJuegoActual(new PanelJuegoRed(cliente, this::volverAlMenu)));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "No se pudo conectar al servidor en " + hostFinal + ":" + puerto + "\n"
                                + "¿Está Servidor.java corriendo?\n\nDetalle: " + e.getMessage(),
                        "Error de conexión", JOptionPane.ERROR_MESSAGE));
            }
        }, "hilo-conexion").start();
    }

    private void reemplazarJuegoActual(PanelBase nuevo) {
        if (juegoActual != null) {
            juegoActual.detener();
            contenedor.remove(juegoActual);
        }
        juegoActual = nuevo;
        contenedor.add(juegoActual, "juego");
        cardLayout.show(contenedor, "juego");
    }

    private void volverAlMenu() {
        cardLayout.show(contenedor, "menu");
    }

    // ======================================================================
    //  Pantalla de inicio (antes PanelMenu.java)
    // ======================================================================
    private static class PanelMenu extends PanelBase {

        PanelMenu(Runnable alJugar, Runnable alJugarRed) {
            setLayout(new GridBagLayout());

            JPanel caja = new JPanel();
            caja.setOpaque(false);
            caja.setLayout(new BoxLayout(caja, BoxLayout.Y_AXIS));

            JLabel titulo = new JLabel("BATALLA NAVAL");
            titulo.setFont(new Font("SansSerif", Font.BOLD, 44));
            titulo.setForeground(Color.WHITE);
            titulo.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel subtitulo = new JLabel("Jugador vs Computadora o por red");
            subtitulo.setFont(new Font("SansSerif", Font.PLAIN, 16));
            subtitulo.setForeground(new Color(220, 235, 245));
            subtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

            JButton botonJugar = crearBoton("Jugar contra la PC (local)", new Color(20, 130, 90));
            botonJugar.addActionListener(e -> {
                Sonido.reproducir("click");
                alJugar.run();
            });

            JButton botonJugarRed = crearBoton("Jugar en red (Servidor.java)", new Color(30, 95, 150));
            botonJugarRed.addActionListener(e -> {
                Sonido.reproducir("click");
                alJugarRed.run();
            });

            JToggleButton botonSonido = new JToggleButton("\uD83D\uDD0A Sonido: activado");
            botonSonido.setSelected(true);
            estilizarSecundario(botonSonido);
            botonSonido.addActionListener(e -> {
                boolean activo = botonSonido.isSelected();
                Sonido.setActivado(activo);
                botonSonido.setText(activo ? "\uD83D\uDD0A Sonido: activado" : "\uD83D\uDD07 Sonido: silenciado");
            });

            caja.add(titulo);
            caja.add(Box.createVerticalStrut(4));
            caja.add(subtitulo);
            caja.add(Box.createVerticalStrut(50));
            caja.add(botonJugar);
            caja.add(Box.createVerticalStrut(12));
            caja.add(botonJugarRed);
            caja.add(Box.createVerticalStrut(16));
            caja.add(botonSonido);

            add(caja);
        }

        private JButton crearBoton(String texto, Color color) {
            JButton boton = new JButton(texto);
            boton.setFont(new Font("SansSerif", Font.BOLD, 20));
            boton.setForeground(Color.WHITE);
            boton.setBackground(color);
            boton.setFocusPainted(false);
            boton.setBorder(BorderFactory.createEmptyBorder(14, 34, 14, 34));
            boton.setAlignmentX(Component.CENTER_ALIGNMENT);
            boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return boton;
        }

        private void estilizarSecundario(JToggleButton boton) {
            boton.setFont(new Font("SansSerif", Font.PLAIN, 15));
            boton.setForeground(Color.WHITE);
            boton.setBackground(new Color(255, 255, 255, 40));
            boton.setFocusPainted(false);
            boton.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
            boton.setAlignmentX(Component.CENTER_ALIGNMENT);
            boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            boton.setContentAreaFilled(true);
            boton.setOpaque(true);
        }

        @Override
        protected void dibujarContenido(Graphics2D g2) {
            // el fondo de agua ya lo dibuja PanelBase; aquí no se necesita nada extra,
            // los botones son componentes Swing normales agregados con add(...)
        }
    }
}
