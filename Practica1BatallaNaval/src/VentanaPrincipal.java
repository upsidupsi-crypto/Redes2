import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class VentanaPrincipal extends JFrame {

    private static final String PANTALLA_MENU = "menu";
    private static final String PANTALLA_JUEGO = "juego";

    private final CardLayout administradorDePantallas = new CardLayout();
    private final JPanel contenedorDePantallas = new JPanel(administradorDePantallas);
    private final PanelMenu panelMenu;
    private PanelJuego juegoActual;

    public static void main(String[] argumentos) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                VentanaPrincipal ventana = new VentanaPrincipal();
                ventana.setVisible(true);
            }
        });
    }

    public VentanaPrincipal() {
        super("Batalla Naval");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 720);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);

        Sonido.precargar(new String[]{"click", "colocar", "radar", "splash",
                "explosion", "explosion_grande", "victoria", "derrota"});

        panelMenu = new PanelMenu(new Runnable() {
            public void run() {
                solicitarDatosYConectar();
            }
        });
        contenedorDePantallas.add(panelMenu, PANTALLA_MENU);
        administradorDePantallas.show(contenedorDePantallas, PANTALLA_MENU);

        setContentPane(contenedorDePantallas);
    }

    private void solicitarDatosYConectar() {
        JTextField campoDireccion = new JTextField("localhost", 15);
        JTextField campoPuerto = new JTextField("5000", 15);
        JTextField campoNombre = new JTextField("Jugador", 15);

        JPanel panelDeDatos = new JPanel(new GridLayout(3, 2, 6, 6));
        panelDeDatos.add(new JLabel("IP o nombre del servidor:"));
        panelDeDatos.add(campoDireccion);
        panelDeDatos.add(new JLabel("Puerto:"));
        panelDeDatos.add(campoPuerto);
        panelDeDatos.add(new JLabel("Tu nombre:"));
        panelDeDatos.add(campoNombre);

        int opcionElegida = JOptionPane.showConfirmDialog(this, panelDeDatos, "Conectar con el servidor",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opcionElegida != JOptionPane.OK_OPTION) {
            return;
        }

        final String direccionServidor = campoDireccion.getText().trim();
        String textoDelPuerto = campoPuerto.getText().trim();
        String nombreEscrito = campoNombre.getText().trim();

        if (direccionServidor.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Escribe la dirección del servidor.", "Dato faltante", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int puertoLeido;
        try {
            puertoLeido = Integer.parseInt(textoDelPuerto);
        } catch (NumberFormatException excepcion) {
            JOptionPane.showMessageDialog(this, "El puerto debe ser un número.", "Puerto inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (puertoLeido < 1 || puertoLeido > 65535) {
            JOptionPane.showMessageDialog(this, "El puerto debe estar entre 1 y 65535.", "Puerto inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int puertoServidor = puertoLeido;
        final String nombreJugador;
        if (nombreEscrito.isEmpty()) {
            nombreJugador = "Jugador";
        } else {
            nombreJugador = nombreEscrito;
        }

        panelMenu.setBotonJugarHabilitado(false);

        Thread hiloDeConexion = new Thread(new Runnable() {
            public void run() {
                Cliente clienteConectado = null;
                try {
                    clienteConectado = new Cliente(direccionServidor, puertoServidor);
                    clienteConectado.enviarNombreYEsperarInicio(nombreJugador);
                    final Cliente clienteListo = clienteConectado;
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            mostrarJuego(clienteListo);
                        }
                    });
                } catch (Exception excepcion) {
                    if (clienteConectado != null) {
                        clienteConectado.cerrar();
                    }
                    final String detalleDelError = excepcion.getMessage();
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            panelMenu.setBotonJugarHabilitado(true);
                            JOptionPane.showMessageDialog(VentanaPrincipal.this,
                                    "No se pudo iniciar la partida en " + direccionServidor + ":" + puertoServidor + "\n"
                                            + "Verifica que Servidor.java esté ejecutándose.\n\nDetalle: " + detalleDelError,
                                    "Error de conexión", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        });
        hiloDeConexion.start();
    }

    private void mostrarJuego(Cliente cliente) {
        if (juegoActual != null) {
            juegoActual.detener();
            contenedorDePantallas.remove(juegoActual);
        }
        juegoActual = new PanelJuego(cliente, new Runnable() {
            public void run() {
                volverAlMenu();
            }
        });
        contenedorDePantallas.add(juegoActual, PANTALLA_JUEGO);
        administradorDePantallas.show(contenedorDePantallas, PANTALLA_JUEGO);
    }

    private void volverAlMenu() {
        panelMenu.setBotonJugarHabilitado(true);
        administradorDePantallas.show(contenedorDePantallas, PANTALLA_MENU);
    }

    private static class PanelMenu extends PanelBase {

        private final JButton botonJugar;

        PanelMenu(final Runnable accionJugar) {
            setLayout(new GridBagLayout());

            JPanel cajaCentral = new JPanel();
            cajaCentral.setOpaque(false);
            cajaCentral.setLayout(new BoxLayout(cajaCentral, BoxLayout.Y_AXIS));

            JLabel etiquetaDeTitulo = new JLabel("BATALLA NAVAL");
            etiquetaDeTitulo.setFont(new Font("SansSerif", Font.BOLD, 44));
            etiquetaDeTitulo.setForeground(Color.WHITE);
            etiquetaDeTitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel etiquetaDeSubtitulo = new JLabel("Jugador vs PC (cliente-servidor)");
            etiquetaDeSubtitulo.setFont(new Font("SansSerif", Font.PLAIN, 16));
            etiquetaDeSubtitulo.setForeground(new Color(220, 235, 245));
            etiquetaDeSubtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

            botonJugar = new JButton("Jugar");
            botonJugar.setFont(new Font("SansSerif", Font.BOLD, 20));
            botonJugar.setForeground(Color.WHITE);
            botonJugar.setBackground(new Color(20, 130, 90));
            botonJugar.setFocusPainted(false);
            botonJugar.setBorder(BorderFactory.createEmptyBorder(14, 34, 14, 34));
            botonJugar.setAlignmentX(Component.CENTER_ALIGNMENT);
            botonJugar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            botonJugar.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evento) {
                    Sonido.reproducir("click");
                    accionJugar.run();
                }
            });

            final JToggleButton botonSonido = new JToggleButton("Sonido: activado");
            botonSonido.setSelected(true);
            botonSonido.setFont(new Font("SansSerif", Font.PLAIN, 15));
            botonSonido.setForeground(Color.WHITE);
            botonSonido.setBackground(new Color(255, 255, 255, 40));
            botonSonido.setFocusPainted(false);
            botonSonido.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
            botonSonido.setAlignmentX(Component.CENTER_ALIGNMENT);
            botonSonido.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            botonSonido.setContentAreaFilled(true);
            botonSonido.setOpaque(true);
            botonSonido.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evento) {
                    boolean sonidoActivo = botonSonido.isSelected();
                    Sonido.setSonidoActivado(sonidoActivo);
                    if (sonidoActivo) {
                        botonSonido.setText("Sonido: activado");
                    } else {
                        botonSonido.setText("Sonido: silenciado");
                    }
                }
            });

            cajaCentral.add(etiquetaDeTitulo);
            cajaCentral.add(Box.createVerticalStrut(4));
            cajaCentral.add(etiquetaDeSubtitulo);
            cajaCentral.add(Box.createVerticalStrut(50));
            cajaCentral.add(botonJugar);
            cajaCentral.add(Box.createVerticalStrut(16));
            cajaCentral.add(botonSonido);

            add(cajaCentral);
        }

        void setBotonJugarHabilitado(boolean habilitado) {
            botonJugar.setEnabled(habilitado);
        }

        protected void dibujarContenido(Graphics2D graficos) {
        }
    }
}