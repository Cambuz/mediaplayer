import javafx.scene.control.TextInputDialog;
import java.io.*;
import java.util.Optional;
import java.util.Properties;

public class FtpConfig {
    private static final String ENV_FILE = "ftp.env";
    public String host;
    public String user;
    public String pass;
    public String port;

    public FtpConfig() {
        Properties props = new Properties();
        File env = new File(ENV_FILE);
        if (env.exists()) {
            try (FileInputStream fis = new FileInputStream(env)) {
                props.load(fis);
                host = props.getProperty("host");
                user = props.getProperty("user");
                pass = props.getProperty("pass");
                port = props.getProperty("port", "21");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            host = prompt("Adresse FTP :");
            user = prompt("Utilisateur :");
            pass = prompt("Mot de passe :");
            port = prompt("Port (par défaut 21) :");
            props.setProperty("host", host);
            props.setProperty("user", user);
            props.setProperty("pass", pass);
            props.setProperty("port", port.isEmpty() ? "21" : port);
            try (FileOutputStream fos = new FileOutputStream(env)) {
                props.store(fos, "FTP Configuration");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String prompt(String message) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(message);
        Optional<String> result = dialog.showAndWait();
        return result.orElse("");
    }
}
