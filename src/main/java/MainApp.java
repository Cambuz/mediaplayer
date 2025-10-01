import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.commons.net.ftp.FTPClient;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class MainApp extends Application {
    private ListView<String> listView;
    private List<String> fileNames;
    private javafx.scene.media.MediaPlayer mediaPlayer;
    private FtpConfig config; // Instance unique pour toute l'appli

    @Override
    public void start(Stage primaryStage) {
        config = new FtpConfig(); // Initialisation une seule fois

        listView = new ListView<>();
        Button playBtn = new Button("Play");
        Button stopBtn = new Button("Stop");
        Button randomBtn = new Button("Lecture aléatoire");


        playBtn.setOnAction(e -> playSelected());
        stopBtn.setOnAction(e -> stopPlayback());
        randomBtn.setOnAction(e -> playRandomSequence());

        HBox controls = new HBox(10, playBtn, stopBtn, randomBtn);
        VBox root = new VBox(10, listView, controls);

        loadFileNamesFromFTP();

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.setTitle("FTP Audio Player");
        primaryStage.show();
    }

    private void loadFileNamesFromFTP() {
        new Thread(() -> {
            try {
                FTPClient ftp = new FTPClient();
                ftp.connect(config.host);
                ftp.login(config.user, config.pass);

                ftp.enterLocalPassiveMode();
                ftp.setControlEncoding("UTF-8");
                ftp.sendCommand("OPTS UTF8 ON");
                String[] files = ftp.listNames();
                ftp.logout();
                ftp.disconnect();
                if (files != null) {
                    fileNames = Arrays.asList(files);
                    javafx.application.Platform.runLater(() -> listView.getItems().setAll(fileNames));
                } else {
                    javafx.application.Platform.runLater(() -> listView.getItems().setAll("Aucun fichier trouvé"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> listView.getItems().setAll("Erreur FTP"));
            }
        }).start();
    }

    private void playSelected() {
        String selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        new Thread(() -> {
            FTPClient ftp = new FTPClient();
            try {
                String tempDir = System.getProperty("java.io.tmpdir");
                java.nio.file.Path subDir = java.nio.file.Paths.get(tempDir, "audio_temp");
                java.nio.file.Files.createDirectories(subDir);

                java.nio.file.Path tempFile = subDir.resolve(java.net.URLDecoder.decode(selected, java.nio.charset.StandardCharsets.UTF_8));

                if (!java.nio.file.Files.exists(tempFile)) {
                    ftp.connect(config.host);
                    ftp.login(config.user, config.pass);

                    ftp.enterLocalPassiveMode();

                    try (InputStream is = ftp.retrieveFileStream(selected)) {
                        if (is != null) {
                            java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            ftp.completePendingCommand();
                        } else {
                            System.out.println("Erreur lors du téléchargement");
                            return;
                        }
                    }
                    ftp.logout();
                    ftp.disconnect();
                }

                javafx.application.Platform.runLater(() -> {
                    if (mediaPlayer != null) mediaPlayer.stop();
                    javafx.scene.media.Media media = new javafx.scene.media.Media(tempFile.toUri().toString());
                    mediaPlayer = new javafx.scene.media.MediaPlayer(media);
                    mediaPlayer.play();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    // shuffle la liste de lecture et utilise la méthode  qui joue le prochain fichier dans la séquence

    private void playRandomSequence() {
        if (fileNames == null || fileNames.isEmpty()) return;
        List<String> shuffled = new java.util.ArrayList<>(fileNames);
        java.util.Collections.shuffle(shuffled);
        playNextInSequence(shuffled, 0);
    }

    // joue le prochain fichier dans la séquence

    private void playNextInSequence(List<String> sequence, int index) {
        if (index >= sequence.size()) return;
        String file = sequence.get(index);

        new Thread(() -> {
            FTPClient ftp = new FTPClient();
            try {
                String tempDir = System.getProperty("java.io.tmpdir");
                java.nio.file.Path subDir = java.nio.file.Paths.get(tempDir, "audio_temp");
                java.nio.file.Files.createDirectories(subDir);

                java.nio.file.Path tempFile = subDir.resolve(java.net.URLDecoder.decode(file, java.nio.charset.StandardCharsets.UTF_8));

                if (!java.nio.file.Files.exists(tempFile)) {
                    ftp.connect(config.host);
                    ftp.login(config.user, config.pass);
                    ftp.enterLocalPassiveMode();

                    try (InputStream is = ftp.retrieveFileStream(file)) {
                        if (is != null) {
                            java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            ftp.completePendingCommand();
                        } else {
                            System.out.println("Erreur lors du téléchargement");
                            return;
                        }
                    }
                    ftp.logout();
                    ftp.disconnect();
                }

                javafx.application.Platform.runLater(() -> {
                    if (mediaPlayer != null) mediaPlayer.stop();
                    javafx.scene.media.Media media = new javafx.scene.media.Media(tempFile.toUri().toString());
                    mediaPlayer = new javafx.scene.media.MediaPlayer(media);
                    mediaPlayer.setOnEndOfMedia(() -> playNextInSequence(sequence, index + 1));
                    mediaPlayer.play();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

}
