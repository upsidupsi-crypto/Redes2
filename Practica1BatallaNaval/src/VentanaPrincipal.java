import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class VentanaPrincipal extends Application {

    private Stage stagePrincipal;
    private StackPane contenedorPrincipal;
    private PanelJuego juegoActual;

    @Override
    public void start(Stage stage) {
        this.stagePrincipal = stage;
        this.stagePrincipal.setTitle("Batalla Naval");

        contenedorPrincipal = new StackPane();
        mostrarMenuPrincipal();

        Scene scene = new Scene(contenedorPrincipal, 1000, 720);

        stagePrincipal.setScene(scene);
        stagePrincipal.setMinWidth(800);
        stagePrincipal.setMinHeight(600);
        stagePrincipal.show();

        Sonido.precargar(new String[]{"click", "colocar", "radar", "splash",
                "explosion", "explosion_grande", "victoria", "derrota"});
    }

    public void mostrarMenuPrincipal() {
        VBox menuBox = new VBox(20);
        menuBox.setAlignment(Pos.CENTER);
        menuBox.setStyle("-fx-background-color: linear-gradient(to bottom, #092d4f, #0f5c82);");

        Label titulo = new Label("BATALLA NAVAL");
        titulo.setStyle("-fx-font-size: 44px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitulo = new Label("Jugador vs PC (Cliente - Servidor)");
        subtitulo.setStyle("-fx-font-size: 16px; -fx-text-fill: #dcebf5;");

        Button btnJugar = new Button("Jugar");
        btnJugar.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-background-color: #14825a; -fx-text-fill: white; -fx-padding: 12 30; -fx-background-radius: 8; -fx-cursor: hand;");
        btnJugar.setOnAction(e -> {
            Sonido.reproducir("click");
            solicitarDatosYConectar();
        });

        ToggleButton btnSonido = new ToggleButton("Sonido: Activado");
        btnSonido.setSelected(true);
        btnSonido.setStyle("-fx-font-size: 14px; -fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-padding: 8 20; -fx-cursor: hand;");
        btnSonido.setOnAction(e -> {
            boolean activo = btnSonido.isSelected();
            Sonido.setSonidoActivado(activo);
            btnSonido.setText(activo ? "Sonido: Activado" : "Sonido: Silenciado");
        });

        menuBox.getChildren().addAll(titulo, subtitulo, btnJugar, btnSonido);
        contenedorPrincipal.getChildren().setAll(menuBox);
    }

    private void solicitarDatosYConectar() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Conectar con el Servidor");
        dialog.setHeaderText("Ingresa los datos para conectar al servidor:");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        TextField ipField = new TextField("localhost");
        TextField puertoField = new TextField("5000");
        TextField nombreField = new TextField("Jugador");

        grid.add(new Label("IP del servidor:"), 0, 0);
        grid.add(ipField, 1, 0);
        grid.add(new Label("Puerto:"), 0, 1);
        grid.add(puertoField, 1, 1);
        grid.add(new Label("Tu nombre:"), 0, 2);
        grid.add(nombreField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String ip = ipField.getText().trim();
                String puertoStr = puertoField.getText().trim();
                String nombre = nombreField.getText().trim();

                try {
                    int puerto = Integer.parseInt(puertoStr);
                    conectarYIniciarJuego(ip, puerto, nombre);
                } catch (NumberFormatException ex) {
                    mostrarAlertaError("Puerto inválido", "El puerto debe ser un número entre 1 y 65535.");
                }
            }
        });
    }

    private void conectarYIniciarJuego(String ip, int puerto, String nombre) {
        new Thread(() -> {
            Cliente cliente = null;
            try {
                cliente = new Cliente(ip, puerto);
                cliente.enviarNombreYEsperarInicio(nombre.isEmpty() ? "Jugador" : nombre);

                Cliente clienteFinal = cliente;
                javafx.application.Platform.runLater(() -> {
                    juegoActual = new PanelJuego(clienteFinal, this::mostrarMenuPrincipal);
                    contenedorPrincipal.getChildren().setAll(juegoActual);
                });
            } catch (Exception ex) {
                if (cliente != null) cliente.cerrar();
                javafx.application.Platform.runLater(() ->
                        mostrarAlertaError("Error de conexión", "No se pudo conectar con el servidor:\n" + ex.getMessage())
                );
            }
        }).start();
    }

    private void mostrarAlertaError(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}