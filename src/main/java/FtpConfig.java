import javafx.scene.control.TextInputDialog;
import java.io.*;
import java.util.Optional;
import java.util.Properties;

public class FtpConfig {
    private static final String ENV_FILE = "ftp.env";
    public String host;
    public String user;
    public String pass;

    public FtpConfig() {
        Properties props = new Properties();
        File env = new File(ENV_FILE);
        if (env.exists()) {
            try (FileInputStream fis = new FileInputStream(env)) {
                props.load(fis);
                host = props.getProperty("host");
                user = props.getProperty("user");
                pass = props.getProperty("pass");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            host = prompt("Adresse FTP :");
            user = prompt("Utilisateur :");
            pass = prompt("Mot de passe :");
            props.setProperty("host", host);
            props.setProperty("user", user);
            props.setProperty("pass", pass);
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
