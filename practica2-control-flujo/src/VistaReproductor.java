import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class VistaReproductor extends Application {

    private ClienteMultimedia cliente;
    private final ReproductorAudio reproductorAudio = new ReproductorAudio();

    private List<MetadatosCancion> listaCompletaCanciones = new ArrayList<>();
    private ListView<MetadatosCancion> listaCanciones;
    private TextField txtBuscar;

    private Label lblEstado;
    private Label lblTitulo, lblArtista, lblAlbum, lblGenero, lblAnio, lblDuracion;
    private Button btnPlayPause, btnStop, btnConectar;

    // Barra de tiempo
    private Slider sliderTiempo;
    private Label lblTiempoActual, lblTiempoTotal;
    private Timeline temporizadorTiempo;
    private long segundosTranscurridos = 0;
    private long duracionTotal = 0;
    private MetadatosCancion cancionActual;

    // Animación de fondo
    private Canvas canvasFondo;
    private AnimationTimer temporizadorAnimacion;
    private final double[] bandasEspectro = new double[20];
    private final Random aleatorio = new Random();
    private double faseOnda = 0;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Reproductor musical");

        StackPane raiz = new StackPane();
        canvasFondo = new Canvas(1000, 720);
        canvasFondo.widthProperty().bind(raiz.widthProperty());
        canvasFondo.heightProperty().bind(raiz.heightProperty());

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(20));
        layout.setTop(construirBarraSuperior());
        layout.setCenter(construirPanelCentral());
        layout.setBottom(construirPanelInferior());

        raiz.getChildren().addAll(canvasFondo, layout);

        iniciarAnimacionFondo();
        iniciarContadorTiempo();

        Scene scene = new Scene(raiz, 1050, 740);
        stage.setScene(scene);
        stage.setMinWidth(850);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(e -> detenerTodo());
        stage.show();
    }

    private BorderPane construirBarraSuperior() {
        BorderPane barra = new BorderPane();
        barra.setPadding(new Insets(0, 0, 15, 0));

        Label titulo = new Label("Reproductor musical");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        titulo.setTextFill(Color.web("#2C3E50"));
        titulo.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        HBox cajaConexion = new HBox(10);
        cajaConexion.setAlignment(Pos.CENTER_RIGHT);

        TextField txtHost = new TextField("localhost");
        txtHost.setPrefWidth(110);
        txtHost.setStyle(estiloCampoWii());

        TextField txtPuerto = new TextField("9876");
        txtPuerto.setPrefWidth(65);
        txtPuerto.setStyle(estiloCampoWii());

        btnConectar = new Button("Conectar");
        btnConectar.setStyle(estiloBotonWii("#00C6FF", "#0072FF", "#FFFFFF"));
        btnConectar.setOnAction(e -> conectarAlServidor(txtHost.getText().trim(), txtPuerto.getText().trim()));

        cajaConexion.getChildren().addAll(
                crearEtiquetaTexto("Servidor:"), txtHost,
                crearEtiquetaTexto("Puerto:"), txtPuerto,
                btnConectar
        );

        barra.setLeft(titulo);
        barra.setRight(cajaConexion);
        return barra;
    }

    private HBox construirPanelCentral() {
        HBox panel = new HBox(20);
        panel.setAlignment(Pos.CENTER);

        // Columna Izquierda: Buscador y Lista de canciones
        VBox cajaLista = new VBox(10);

        // Buscador por nombre, artista o género
        txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 Buscar por nombre, artista o género...");
        txtBuscar.setStyle(estiloCampoWii());
        txtBuscar.textProperty().addListener((obs, viejo, nuevo) -> filtrarCanciones(nuevo));

        listaCanciones = new ListView<>();
        listaCanciones.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); " +
                "-fx-background-radius: 15; -fx-border-radius: 15; " +
                "-fx-border-color: #E2E8F0; -fx-border-width: 1.5; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 10, 0, 0, 4);");

        listaCanciones.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(MetadatosCancion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(String.format("%s  —  %s  [%s]", item.obtenerTitulo(), item.obtenerArtista(), item.obtenerGenero()));
                    setTextFill(Color.web("#2C3E50"));
                    setFont(Font.font("Segoe UI", 14));
                    setStyle("-fx-padding: 8; -fx-background-color: transparent;");
                }
            }
        });

        // Evento de Doble Clic para reproducir directamente
        listaCanciones.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && listaCanciones.getSelectionModel().getSelectedItem() != null) {
                reproducirCancion(listaCanciones.getSelectionModel().getSelectedItem());
            }
        });

        listaCanciones.getSelectionModel().selectedItemProperty().addListener((obs, viejo, nuevo) -> actualizarPanelMetadatos(nuevo));

        VBox.setVgrow(listaCanciones, Priority.ALWAYS);
        cajaLista.getChildren().addAll(txtBuscar, listaCanciones);
        HBox.setHgrow(cajaLista, Priority.ALWAYS);

        // Columna Derecha: Panel de metadatos ID3
        VBox cajaMetadatos = new VBox(14);
        cajaMetadatos.setPrefWidth(330);
        cajaMetadatos.setPadding(new Insets(20));
        cajaMetadatos.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); " +
                "-fx-background-radius: 18; -fx-border-radius: 18; " +
                "-fx-border-color: #E2E8F0; -fx-border-width: 1.5; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 12, 0, 0, 5);");

        Label lblMetaTitulo = new Label("Información");
        lblMetaTitulo.setTextFill(Color.web("#0088FF"));
        lblMetaTitulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));

        lblTitulo = crearEtiquetaMetadato("Título: —");
        lblArtista = crearEtiquetaMetadato("Artista: —");
        lblAlbum = crearEtiquetaMetadato("Álbum: —");
        lblGenero = crearEtiquetaMetadato("Género: —");
        lblAnio = crearEtiquetaMetadato("Año: —");
        lblDuracion = crearEtiquetaMetadato("Duración: —");

        cajaMetadatos.getChildren().addAll(lblMetaTitulo, lblTitulo, lblArtista, lblAlbum, lblGenero, lblAnio, lblDuracion);

        panel.getChildren().addAll(cajaLista, cajaMetadatos);
        return panel;
    }

    private VBox construirPanelInferior() {
        VBox contenedor = new VBox(10);
        contenedor.setPadding(new Insets(10, 0, 0, 0));
        contenedor.setAlignment(Pos.CENTER);

        lblEstado = new Label("Esperando conexión...");
        lblEstado.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        lblEstado.setTextFill(Color.web("#546E7A"));

        // Barra de Tiempo y Progreso
        HBox cajaBarraTiempo = new HBox(12);
        cajaBarraTiempo.setAlignment(Pos.CENTER);
        cajaBarraTiempo.setPadding(new Insets(5, 20, 5, 20));

        lblTiempoActual = new Label("0:00");
        lblTiempoActual.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblTiempoActual.setTextFill(Color.web("#546E7A"));

        sliderTiempo = new Slider(0, 100, 0);
        sliderTiempo.setDisable(true);
        HBox.setHgrow(sliderTiempo, Priority.ALWAYS);
        sliderTiempo.setStyle("-fx-control-inner-background: #E2E8F0; -fx-accent: #0072FF;");

        lblTiempoTotal = new Label("0:00");
        lblTiempoTotal.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblTiempoTotal.setTextFill(Color.web("#546E7A"));

        cajaBarraTiempo.getChildren().addAll(lblTiempoActual, sliderTiempo, lblTiempoTotal);

        // Controles principales: Botón único Play/Pausa estilo Spotify + Detener
        HBox controles = new HBox(15);
        controles.setAlignment(Pos.CENTER);

        btnPlayPause = new Button("▶  Play");
        btnPlayPause.setStyle(estiloBotonWii("#00C6FF", "#0072FF", "#FFFFFF"));
        btnPlayPause.setOnAction(e -> alternarPlayPausa());

        btnStop = new Button("⏹  Detener");
        btnStop.setStyle(estiloBotonWii("#FF5252", "#FF1744", "#FFFFFF"));
        btnStop.setOnAction(e -> detenerReproduccion());

        controles.getChildren().addAll(btnPlayPause, btnStop);
        contenedor.getChildren().addAll(lblEstado, cajaBarraTiempo, controles);
        return contenedor;
    }

    private Label crearEtiquetaTexto(String texto) {
        Label l = new Label(texto);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        l.setTextFill(Color.web("#4A5568"));
        return l;
    }

    private Label crearEtiquetaMetadato(String textoInicial) {
        Label l = new Label(textoInicial);
        l.setFont(Font.font("Segoe UI", 13));
        l.setTextFill(Color.web("#4A5568"));
        l.setWrapText(true);
        return l;
    }

    private String estiloCampoWii() {
        return "-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; " +
                "-fx-border-radius: 12; -fx-background-radius: 12; " +
                "-fx-text-fill: #1E293B; -fx-font-family: 'Segoe UI'; -fx-padding: 8 12;";
    }

    private String estiloBotonWii(String colorGradiente1, String colorGradiente2, String colorTexto) {
        return String.format(
                "-fx-background-color: linear-gradient(to bottom, %s, %s); " +
                        "-fx-text-fill: %s; -fx-font-family: 'Segoe UI'; -fx-font-weight: bold; " +
                        "-fx-background-radius: 20; -fx-cursor: hand; -fx-padding: 8 26; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.12), 6, 0, 0, 3);",
                colorGradiente1, colorGradiente2, colorTexto
        );
    }

    private void conectarAlServidor(String host, String puertoStr) {
        try {
            int puerto = Integer.parseInt(puertoStr);
            lblEstado.setText("Cargando catálogo...");
            btnConectar.setDisable(true);

            new Thread(() -> {
                try {
                    cliente = new ClienteMultimedia(host, puerto, 0.0);
                    listaCompletaCanciones = cliente.solicitarCatalogo();

                    Platform.runLater(() -> {
                        listaCanciones.getItems().setAll(listaCompletaCanciones);
                        lblEstado.setText("Conectado: " + listaCompletaCanciones.size() + " canciones disponibles");
                        btnConectar.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        mostrarError("Error de conexión", ex.getMessage());
                        lblEstado.setText("No se pudo establecer la conexión");
                        btnConectar.setDisable(false);
                    });
                }
            }).start();

        } catch (NumberFormatException ex) {
            mostrarError("Puerto inválido", "El puerto debe ser un número entero.");
        }
    }

    private void filtrarCanciones(String texto) {
        if (listaCompletaCanciones == null || listaCompletaCanciones.isEmpty()) return;
        if (texto == null || texto.isBlank()) {
            listaCanciones.getItems().setAll(listaCompletaCanciones);
            return;
        }

        String consulta = texto.toLowerCase().trim();
        List<MetadatosCancion> filtradas = listaCompletaCanciones.stream()
                .filter(c -> c.obtenerTitulo().toLowerCase().contains(consulta)
                        || c.obtenerArtista().toLowerCase().contains(consulta)
                        || c.obtenerGenero().toLowerCase().contains(consulta))
                .collect(Collectors.toList());

        listaCanciones.getItems().setAll(filtradas);
    }

    private void alternarPlayPausa() {
        // Si está en reproducción activa -> pausar
        if (reproductorAudio.estaActivo() && !reproductorAudio.estaPausado()) {
            reproductorAudio.pausar();
            btnPlayPause.setText("▶  Play");
            lblEstado.setText("En pausa: " + (cancionActual != null ? cancionActual.obtenerTitulo() : ""));
            return;
        }

        // Si está pausado -> reanudar
        if (reproductorAudio.estaPausado()) {
            reproductorAudio.reanudar();
            btnPlayPause.setText("⏸  Pausa");
            lblEstado.setText("Reproduciendo: " + (cancionActual != null ? cancionActual.obtenerTitulo() : ""));
            return;
        }

        // Si no hay reproducción -> reproducir la canción seleccionada en la lista
        MetadatosCancion seleccionada = listaCanciones.getSelectionModel().getSelectedItem();
        if (seleccionada != null) {
            reproducirCancion(seleccionada);
        } else {
            lblEstado.setText("Selecciona una canción de la lista");
        }
    }

    private void reproducirCancion(MetadatosCancion seleccionada) {
        if (seleccionada == null) return;

        cancionActual = seleccionada;
        duracionTotal = seleccionada.obtenerDuracionSegundos();
        segundosTranscurridos = 0;

        sliderTiempo.setMin(0);
        sliderTiempo.setMax(duracionTotal > 0 ? duracionTotal : 100);
        sliderTiempo.setValue(0);
        lblTiempoActual.setText("0:00");
        lblTiempoTotal.setText(seleccionada.obtenerDuracionFormateada());

        lblEstado.setText("Descargando canción mediante Go-Back-N...");
        btnPlayPause.setText("⏸  Pausa");

        new Thread(() -> {
            try {
                Path archivoLocal = cliente.descargarCancion(seleccionada);
                reproductorAudio.reproducir(archivoLocal);

                Platform.runLater(() -> lblEstado.setText("Reproduciendo: " + seleccionada.obtenerTitulo()));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    mostrarError("Error de reproducción", ex.getMessage());
                    lblEstado.setText("Fallo al descargar la canción");
                    btnPlayPause.setText("▶  Play");
                });
            }
        }).start();
    }

    private void detenerReproduccion() {
        reproductorAudio.detener();
        segundosTranscurridos = 0;
        sliderTiempo.setValue(0);
        lblTiempoActual.setText("0:00");
        btnPlayPause.setText("▶  Play");
        lblEstado.setText("Reproducción detenida");
    }

    private void actualizarPanelMetadatos(MetadatosCancion m) {
        if (m == null) return;
        lblTitulo.setText("Título: " + m.obtenerTitulo());
        lblArtista.setText("Artista: " + m.obtenerArtista());
        lblAlbum.setText("Álbum: " + m.obtenerAlbum());
        lblGenero.setText("Género: " + m.obtenerGenero());
        lblAnio.setText("Año: " + m.obtenerAnio());
        lblDuracion.setText("Duración: " + m.obtenerDuracionFormateada());
    }

    private void iniciarContadorTiempo() {
        temporizadorTiempo = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (reproductorAudio.estaActivo() && !reproductorAudio.estaPausado()) {
                segundosTranscurridos++;
                if (duracionTotal > 0 && segundosTranscurridos <= duracionTotal) {
                    sliderTiempo.setValue(segundosTranscurridos);
                    lblTiempoActual.setText(formatearTiempo(segundosTranscurridos));
                }
            }
        }));
        temporizadorTiempo.setCycleCount(Timeline.INDEFINITE);
        temporizadorTiempo.play();
    }

    private String formatearTiempo(long segundos) {
        return String.format("%d:%02d", segundos / 60, segundos % 60);
    }

    private void iniciarAnimacionFondo() {
        temporizadorAnimacion = new AnimationTimer() {
            private long ultimoCambio = 0;

            @Override
            public void handle(long horaActual) {
                if (horaActual - ultimoCambio >= 35_000_000L) {
                    faseOnda += 0.03;
                    dibujarFondoWii();
                    ultimoCambio = horaActual;
                }
            }
        };
        temporizadorAnimacion.start();
    }

    private void dibujarFondoWii() {
        double w = canvasFondo.getWidth();
        double h = canvasFondo.getHeight();

        if (w <= 0 || h <= 0) return;

        GraphicsContext g = canvasFondo.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        LinearGradient bg = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#F4F7FB")), new Stop(1, Color.web("#E2E9F3")));
        g.setFill(bg);
        g.fillRect(0, 0, w, h);

        g.setFill(Color.rgb(0, 150, 255, 0.06));
        for (int i = 0; i < 3; i++) {
            double alturaBase = h * (0.65 + i * 0.1);
            g.beginPath();
            g.moveTo(0, alturaBase);
            for (double x = 0; x <= w; x += 20) {
                double y = alturaBase + Math.sin((x * 0.008) + faseOnda * (0.8 + i * 0.2)) * (15 + i * 5);
                g.lineTo(x, y);
            }
            g.lineTo(w, h);
            g.lineTo(0, h);
            g.closePath();
            g.fill();
        }

        boolean reproduciendo = reproductorAudio.estaActivo() && !reproductorAudio.estaPausado();
        double anchoBarra = w / bandasEspectro.length;

        for (int i = 0; i < bandasEspectro.length; i++) {
            if (reproduciendo) {
                bandasEspectro[i] = Math.min(1.0, Math.max(0.08, bandasEspectro[i] + (aleatorio.nextDouble() - 0.48)));
            } else {
                bandasEspectro[i] = Math.max(0.02, bandasEspectro[i] * 0.88);
            }

            double altoBarra = (h * 0.2) * bandasEspectro[i];
            double x = i * anchoBarra + 8;
            double y = h - altoBarra - 10;

            g.setFill(Color.rgb(0, 163, 224, 0.25));
            g.fillRoundRect(x, y, anchoBarra - 16, altoBarra, 12, 12);
        }
    }

    private void mostrarError(String tit, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(tit);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void detenerTodo() {
        if (temporizadorAnimacion != null) temporizadorAnimacion.stop();
        if (temporizadorTiempo != null) temporizadorTiempo.stop();
        reproductorAudio.detener();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}