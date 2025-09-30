# FTP Audio Player

Application JavaFX permettant de lister et lire des fichiers audio stockés sur un serveur FTP.

## Fonctionnalités

- Connexion dynamique à un serveur FTP (configuration à la première utilisation)
- Liste des fichiers distants (support UTF-8)
- Téléchargement temporaire et lecture audio locale
- Interface simple avec boutons Play/Stop

## Prérequis

- Java 11 ou supérieur
- Maven
- Accès à un serveur FTP avec fichiers audio compatibles

## Installation

1. **Cloner le dépôt :**

git clone <url-du-repo> cd <nom-du-repo>

2. **Construire le projet :**

mvn clean install

3. **Lancer l’application :**

mvn javafx:run

## Configuration

À la première exécution, l’application demande l’adresse, l’utilisateur et le mot de passe FTP, puis crée un fichier `ftp.env` à la racine du projet.

**Ne partage pas ce fichier !**  
Il est ignoré par Git grâce à l’entrée suivante dans `.gitignore` :

ftp.env

## Structure du projet

- `src/main/java/MainApp.java` : point d’entrée de l’application
- `src/main/java/FtpConfig.java` : gestion de la configuration FTP

## Dépendances principales

- JavaFX
- Apache Commons Net (FTP)
- Maven

## Sécurité

**Ne versionne jamais le fichier `ftp.env`** contenant tes identifiants FTP.

---

© 2025 Cambuz
