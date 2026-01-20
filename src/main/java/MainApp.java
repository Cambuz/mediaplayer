import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class MainApp extends Application {
    private ListView<String> listView;
    private List<String> fileNames;
    private javafx.scene.media.MediaPlayer mediaPlayer;
    private FtpConfig config; // Instance unique pour toute l'appli

    private ProgressBar downloadProgressBar;
    private Label timeLabel;

    @Override
    public void start(Stage primaryStage) {
        config = new FtpConfig(); // Initialisation une seule fois

        listView = new ListView<>();
        downloadProgressBar = new ProgressBar(0);
        downloadProgressBar.setPrefWidth(380);
        timeLabel = new Label("00:00 / 00:00");

        Button playBtn = new Button("Play");
        Button stopBtn = new Button("Stop");
        Button randomBtn = new Button("Lecture aléatoire");

        playBtn.setOnAction(e -> playSelected());
        stopBtn.setOnAction(e -> stopPlayback());
        randomBtn.setOnAction(e -> playRandomSequence());

        HBox controls = new HBox(10, playBtn, stopBtn, randomBtn);
        VBox root = new VBox(10, listView, downloadProgressBar, timeLabel, controls);

        loadFileNamesFromFTP();

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.setTitle("FTP Audio Player");
        primaryStage.show();
    }

    private void loadFileNamesFromFTP() {
        new Thread(() -> {
            try {
                if ("21".equals(config.port)) {
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
                        fileNames.sort(String.CASE_INSENSITIVE_ORDER);
                        javafx.application.Platform.runLater(() -> listView.getItems().setAll(fileNames));
                    } else {
                        javafx.application.Platform.runLater(() -> listView.getItems().setAll("Aucun fichier trouvé"));
                    }
                } else if ("22".equals(config.port)) {
                    SshClient client = SshClient.setUpDefaultClient();
                    client.start();
                    ClientSession session = null;
                    try {
                        session = client.connect(config.user, config.host, 22).verify(5000).getSession();
                        session.addPasswordIdentity(config.pass);
                        session.auth().verify(5000);

                        try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                            List<String> names = new ArrayList<>();
                            for (SftpClient.DirEntry entry : sftp.readDir("mashups")) {
                                String name = entry.getFilename();
                                if (!".".equals(name) && !"..".equals(name)) names.add(name);
                            }
                            names.sort(String.CASE_INSENSITIVE_ORDER);
                            fileNames = names;
                            javafx.application.Platform.runLater(() -> {
                                if (fileNames != null && !fileNames.isEmpty()) listView.getItems().setAll(fileNames);
                                else listView.getItems().setAll("Aucun fichier trouvé");
                            });
                        }
                    } finally {
                        if (session != null) session.close(false);
                        client.stop();
                    }
                } else {
                    javafx.application.Platform.runLater(() -> listView.getItems().setAll("Port inconnu"));
                }

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> listView.getItems().setAll("Erreur FTP/SFTP"));
            }
        }).start();
    }

    private void playSelected() {
        String selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        new Thread(() -> {
            try {
                String tempDir = System.getProperty("java.io.tmpdir");
                java.nio.file.Path subDir = java.nio.file.Paths.get(tempDir, "audio_temp");
                java.nio.file.Files.createDirectories(subDir);

                java.nio.file.Path tempFile = subDir.resolve(java.net.URLDecoder.decode(selected, java.nio.charset.StandardCharsets.UTF_8));

                if (!java.nio.file.Files.exists(tempFile)) {
                    javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(0));
                    if ("21".equals(config.port)) {
                        FTPClient ftp = new FTPClient();
                        ftp.connect(config.host);
                        ftp.login(config.user, config.pass);
                        ftp.enterLocalPassiveMode();

                        final AtomicLong size = new AtomicLong(-1);
                        FTPFile[] fArr = ftp.listFiles(selected);
                        if (fArr != null && fArr.length > 0) size.set(fArr[0].getSize());

                        try (InputStream is = ftp.retrieveFileStream(selected);
                             OutputStream os = Files.newOutputStream(tempFile)) {
                            if (is != null) {
                                byte[] buffer = new byte[8192];
                                int len;
                                long read = 0;
                                while ((len = is.read(buffer)) != -1) {
                                    os.write(buffer, 0, len);
                                    read += len;
                                    final double prog = (size.get() > 0) ? ((double) read / size.get()) : ProgressIndicator.INDETERMINATE_PROGRESS;
                                    javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(size.get() > 0 ? prog : ProgressIndicator.INDETERMINATE_PROGRESS));
                                }
                                ftp.completePendingCommand();
                            } else {
                                System.out.println("Erreur lors du téléchargement FTP");
                                return;
                            }
                        }
                        ftp.logout();
                        ftp.disconnect();
                    } else if ("22".equals(config.port)) {
                        SshClient client = SshClient.setUpDefaultClient();
                        client.start();
                        ClientSession session = null;
                        try {
                            session = client.connect(config.user, config.host, 22).verify(5000).getSession();
                            session.addPasswordIdentity(config.pass);
                            session.auth().verify(5000);

                            try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                                String remotePath = "mashups/" + selected;
                                final AtomicLong size = new AtomicLong(-1);
                                try {
                                    SftpClient.Attributes attrs = sftp.stat(remotePath);
                                    if (attrs != null) size.set(attrs.getSize());
                                } catch (Exception ignored) {}

                                try (SftpClient.CloseableHandle handle = sftp.open(remotePath, EnumSet.of(SftpClient.OpenMode.Read))) {
                                    try (OutputStream os = Files.newOutputStream(tempFile)) {
                                        long pos = 0;
                                        byte[] buffer = new byte[8192];
                                        int len;
                                        while ((len = sftp.read(handle, pos, buffer, 0, buffer.length)) > 0) {
                                            os.write(buffer, 0, len);
                                            pos += len;
                                            final double prog = (size.get() > 0) ? ((double) pos / size.get()) : ProgressIndicator.INDETERMINATE_PROGRESS;
                                            javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(size.get() > 0 ? prog : ProgressIndicator.INDETERMINATE_PROGRESS));
                                        }
                                    }
                                }
                            }
                        } finally {
                            if (session != null) session.close(false);
                            client.stop();
                        }
                    } else {
                        System.out.println("Port inconnu pour téléchargement");
                        return;
                    }
                    javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(1.0));
                }

                javafx.application.Platform.runLater(() -> {
                    if (mediaPlayer != null) mediaPlayer.stop();
                    javafx.scene.media.Media media = new javafx.scene.media.Media(tempFile.toUri().toString());
                    mediaPlayer = new javafx.scene.media.MediaPlayer(media);

                    mediaPlayer.setOnReady(() -> {
                        Duration total = media.getDuration();
                        timeLabel.setText(formatTime(Duration.ZERO) + " / " + formatTime(total));
                    });

                    mediaPlayer.currentTimeProperty().addListener((obs, oldT, newT) -> {
                        Duration total = media.getDuration();
                        javafx.application.Platform.runLater(() -> timeLabel.setText(formatTime(newT) + " / " + formatTime(total)));
                    });

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

    private void playRandomSequence() {
        if (fileNames == null || fileNames.isEmpty()) return;
        List<String> shuffled = new java.util.ArrayList<>(fileNames);
        java.util.Collections.shuffle(shuffled);
        playNextInSequence(shuffled, 0);
    }

    private void playNextInSequence(List<String> sequence, int index) {
        if (index >= sequence.size()) return;
        String file = sequence.get(index);

        new Thread(() -> {
            try {
                String tempDir = System.getProperty("java.io.tmpdir");
                java.nio.file.Path subDir = java.nio.file.Paths.get(tempDir, "audio_temp");
                java.nio.file.Files.createDirectories(subDir);

                java.nio.file.Path tempFile = subDir.resolve(java.net.URLDecoder.decode(file, java.nio.charset.StandardCharsets.UTF_8));

                if (!java.nio.file.Files.exists(tempFile)) {
                    javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(0));
                    if ("21".equals(config.port)) {
                        FTPClient ftp = new FTPClient();
                        ftp.connect(config.host);
                        ftp.login(config.user, config.pass);
                        ftp.enterLocalPassiveMode();

                        final AtomicLong size = new AtomicLong(-1);
                        FTPFile[] fArr = ftp.listFiles(file);
                        if (fArr != null && fArr.length > 0) size.set(fArr[0].getSize());

                        try (InputStream is = ftp.retrieveFileStream(file);
                             OutputStream os = Files.newOutputStream(tempFile)) {
                            if (is != null) {
                                byte[] buffer = new byte[8192];
                                int len;
                                long read = 0;
                                while ((len = is.read(buffer)) != -1) {
                                    os.write(buffer, 0, len);
                                    read += len;
                                    final double prog = (size.get() > 0) ? ((double) read / size.get()) : ProgressIndicator.INDETERMINATE_PROGRESS;
                                    javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(size.get() > 0 ? prog : ProgressIndicator.INDETERMINATE_PROGRESS));
                                }
                                ftp.completePendingCommand();
                            } else {
                                System.out.println("Erreur lors du téléchargement FTP");
                                return;
                            }
                        }
                        ftp.logout();
                        ftp.disconnect();
                    } else if ("22".equals(config.port)) {
                        SshClient client = SshClient.setUpDefaultClient();
                        client.start();
                        ClientSession session = null;
                        try {
                            session = client.connect(config.user, config.host, 22).verify(5000).getSession();
                            session.addPasswordIdentity(config.pass);
                            session.auth().verify(5000);

                            try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                                String remotePath = "mashups/" + file;
                                final AtomicLong size = new AtomicLong(-1);
                                try {
                                    SftpClient.Attributes attrs = sftp.stat(remotePath);
                                    if (attrs != null) size.set(attrs.getSize());
                                } catch (Exception ignored) {}

                                try (SftpClient.CloseableHandle handle = sftp.open(remotePath, EnumSet.of(SftpClient.OpenMode.Read))) {
                                    try (OutputStream os = Files.newOutputStream(tempFile)) {
                                        long pos = 0;
                                        byte[] buffer = new byte[8192];
                                        int len;
                                        while ((len = sftp.read(handle, pos, buffer, 0, buffer.length)) > 0) {
                                            os.write(buffer, 0, len);
                                            pos += len;
                                            final double prog = (size.get() > 0) ? ((double) pos / size.get()) : ProgressIndicator.INDETERMINATE_PROGRESS;
                                            javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(size.get() > 0 ? prog : ProgressIndicator.INDETERMINATE_PROGRESS));
                                        }
                                    }
                                }
                            }
                        } finally {
                            if (session != null) session.close(false);
                            client.stop();
                        }
                    } else {
                        System.out.println("Port inconnu pour téléchargement");
                        return;
                    }
                    javafx.application.Platform.runLater(() -> downloadProgressBar.setProgress(1.0));
                }

                javafx.application.Platform.runLater(() -> {
                    if (mediaPlayer != null) mediaPlayer.stop();
                    javafx.scene.media.Media media = new javafx.scene.media.Media(tempFile.toUri().toString());
                    mediaPlayer = new javafx.scene.media.MediaPlayer(media);

                    mediaPlayer.setOnReady(() -> {
                        Duration total = media.getDuration();
                        timeLabel.setText(formatTime(Duration.ZERO) + " / " + formatTime(total));
                    });

                    mediaPlayer.currentTimeProperty().addListener((obs, oldT, newT) -> {
                        Duration total = media.getDuration();
                        javafx.application.Platform.runLater(() -> timeLabel.setText(formatTime(newT) + " / " + formatTime(total)));
                    });

                    mediaPlayer.setOnEndOfMedia(() -> playNextInSequence(sequence, index + 1));
                    mediaPlayer.play();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String formatTime(Duration d) {
        if (d == null || d.isUnknown() || d.lessThanOrEqualTo(Duration.ZERO)) return "00:00";
        long totalSeconds = (long) Math.floor(d.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

}
