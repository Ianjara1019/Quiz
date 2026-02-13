package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Client TCP qui se connecte au serveur maître
 * puis est redirigé vers le serveur esclave approprié
 */
public class ClientDistribue {
    private static final String HOST_MAITRE = "localhost";
    private static final int PORT_MAITRE = 6000;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String hostMaitre = HOST_MAITRE;
        String envHost = System.getenv("QUIZ_SERVER_HOST");
        if (envHost != null && !envHost.isBlank()) {
            hostMaitre = envHost.trim();
        }
        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            hostMaitre = args[0].trim();
        }
        String tokenClient = System.getenv("QUIZ_CLIENT_TOKEN");
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║          CLIENT QUIZ TCP               ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        try {
            boolean continuer = true;
            while (continuer) {
                // Étape 1: Se connecter au serveur maître (avec attente)
                System.out.println("→ Connexion au serveur maître...");
                Socket socketMaitre = connecterAuMaitreAvecAttente(hostMaitre, PORT_MAITRE);
                BufferedReader inMaitre = new BufferedReader(
                    new InputStreamReader(socketMaitre.getInputStream()));
                PrintWriter outMaitre = new PrintWriter(
                    socketMaitre.getOutputStream(), true);

                // Étape 2: Choisir le mode
                String message = attendreMessage(inMaitre, socketMaitre);
                if (message == null) {
                    System.out.println("✗ Le serveur maître n'est pas prêt. Nouvelle tentative...");
                    socketMaitre.close();
                    continue;
                }
                if ("MODE?".equals(message)) {
                    System.out.println("\n=== Menu principal ===");
                    System.out.println("1. Jouer");
                    System.out.println("2. Historique personnel (avant theme)");
                    System.out.println("3. Classement global");
                    System.out.println("4. Quitter");
                    System.out.print("Votre choix: ");
                    String mode = sc.nextLine();
                    if ("2".equals(mode)) {
                        System.out.print("Username: ");
                        String user = sc.nextLine();
                        if (tokenClient != null && !tokenClient.isBlank()) {
                            outMaitre.println("HISTORY:" + user + ";TOKEN:" + tokenClient.trim());
                        } else {
                            outMaitre.println("HISTORY:" + user);
                        }
                    } else if ("3".equals(mode)) {
                        if (tokenClient != null && !tokenClient.isBlank()) {
                            outMaitre.println("LEADERBOARD;TOKEN:" + tokenClient.trim());
                        } else {
                            outMaitre.println("LEADERBOARD");
                        }
                    } else if ("4".equals(mode)) {
                        outMaitre.println("QUIT");
                        socketMaitre.close();
                        break;
                    } else {
                        System.out.println("\nThèmes disponibles:");
                        System.out.println("  - Maths");
                        System.out.println("  - Histoire");
                        System.out.println("  - Géographie");
                        System.out.print("\nChoisissez votre thème: ");
                        String theme = sc.nextLine();
                        if (tokenClient != null && !tokenClient.isBlank()) {
                            outMaitre.println("PLAY:" + theme + ";TOKEN:" + tokenClient.trim());
                        } else {
                            outMaitre.println(theme);
                        }
                    }
                }

                // Étape 3: Recevoir la redirection
                String redirection = inMaitre.readLine();
                if (redirection == null) {
                    System.out.println("\n✗ Le serveur maître a fermé la connexion.");
                    socketMaitre.close();
                    break;
                }

                if (redirection.startsWith("ERREUR:")) {
                    System.out.println("\n✗ " + redirection.substring(7));
                    socketMaitre.close();
                    continue;
                }

                if ("HISTORY_BEGIN".equals(redirection)) {
                    System.out.println("\n=== Historique personnel ===");
                    String line;
                    while ((line = inMaitre.readLine()) != null && !"HISTORY_END".equals(line)) {
                        System.out.println(line);
                    }
                    socketMaitre.close();
                    System.out.print("\nVoulez-vous choisir un theme et jouer maintenant ? (o/n): ");
                    String reponse = sc.nextLine();
                    if (reponse.equalsIgnoreCase("o") || reponse.equalsIgnoreCase("oui")) {
                        continue;
                    }
                    break;
                }
                if ("LEADERBOARD_BEGIN".equals(redirection)) {
                    System.out.println("\n=== Classement global ===");
                    String line;
                    while ((line = inMaitre.readLine()) != null && !"LEADERBOARD_END".equals(line)) {
                        System.out.println(line);
                    }
                    socketMaitre.close();
                    System.out.print("\nVoulez-vous choisir un theme et jouer maintenant ? (o/n): ");
                    String reponse = sc.nextLine();
                    if (reponse.equalsIgnoreCase("o") || reponse.equalsIgnoreCase("oui")) {
                        continue;
                    }
                    break;
                }

                if (!redirection.startsWith("REDIRECT:")) {
                    System.out.println("\n✗ Réponse inattendue du serveur");
                    socketMaitre.close();
                    continue;
                }

                // Étape 4: Se connecter au serveur esclave
                String[] serverInfo = redirection.substring(9).split(":");
                String host = serverInfo[0];
                int port = Integer.parseInt(serverInfo[1]);

                socketMaitre.close();

                System.out.println("→ Redirection vers " + host + ":" + port + "\n");
            
                Socket socketEsclave = new Socket(host, port);
                BufferedReader inEsclave = new BufferedReader(
                    new InputStreamReader(socketEsclave.getInputStream()));
                PrintWriter outEsclave = new PrintWriter(
                    socketEsclave.getOutputStream(), true);

                int questionNum = 1;
                boolean termine = false;
                while (!termine) {
                    String msg = inEsclave.readLine();
                    if (msg == null) break;

                if ("AUTH?".equals(msg)) {
                    System.out.print("1=Login, 2=Register: ");
                    String choixAuth = sc.nextLine();
                    System.out.print("Username: ");
                    String user = sc.nextLine();
                    System.out.print("Mot de passe: ");
                    String pass = sc.nextLine();
                    if ("2".equals(choixAuth)) {
                        outEsclave.println("REGISTER:" + user + ";PASS:" + pass);
                    } else {
                        outEsclave.println("LOGIN:" + user + ";PASS:" + pass);
                    }
                }
                else if (msg.startsWith("ERREUR:AUTH")) {
                    System.out.println("✗ " + msg);
                    break;
                }
                else if (msg.startsWith("MENU:")) {
                    System.out.print("Choix (1=Jouer, 2=Historique): ");
                    String choix = sc.nextLine();
                    outEsclave.println(choix);
                }
                else if ("ROOM?".equals(msg)) {
                    System.out.print("Partie privee ? (o/n): ");
                    String priv = sc.nextLine();
                    if (priv.equalsIgnoreCase("o") || priv.equalsIgnoreCase("oui")) {
                        System.out.print("Code de partie: ");
                        String code = sc.nextLine();
                        outEsclave.println("ROOM:" + code.trim());
                    } else {
                        outEsclave.println("ROOM:");
                    }
                }
                else if ("TOUR?".equals(msg)) {
                    System.out.print("Mode tournoi ? (o/n): ");
                    String tour = sc.nextLine();
                    outEsclave.println("TOUR:" + tour.trim());
                }
                else if ("HISTOIRE_BEGIN".equals(msg)) {
                    System.out.println("\n=== Historique ===");
                    while ((msg = inEsclave.readLine()) != null && !"HISTOIRE_END".equals(msg)) {
                        System.out.println(msg);
                    }
                    termine = true;
                }
                else if ("EN_ATTENTE".equals(msg)) {
                    System.out.println("→ En attente d'autres joueurs...");
                }
                else if (msg.startsWith("MATCH_START:")) {
                    System.out.println("\n╔════════════════════════════════════════╗");
                    System.out.println("║         MATCH MULTI-JOUEURS            ║");
                    System.out.println("╚════════════════════════════════════════╝");
                    System.out.println(msg);
                }
                else if (msg.startsWith("QUESTION:")) {
                    System.out.println("Question " + questionNum + ": " + msg.substring(9));
                    System.out.print("Votre réponse: ");
                    String reponse = sc.nextLine();
                    outEsclave.println(reponse);
                    questionNum++;
                }
                else if (msg.startsWith("ROUND_START:")) {
                    System.out.println("\n" + msg);
                }
                else if (msg.startsWith("ROUND_END:")) {
                    System.out.println(msg + "\n");
                }
                else if (msg.startsWith("MATCH_END:")) {
                    System.out.println("\n" + msg);
                    termine = true;
                }
                    else if (!msg.isBlank()) {
                        System.out.println(msg);
                    }
                }

                socketEsclave.close();

                System.out.print("\nVoulez-vous rejouer ? (o/n): ");
                String rejouer = sc.nextLine();
                if (!(rejouer.equalsIgnoreCase("o") || rejouer.equalsIgnoreCase("oui"))) {
                    continuer = false;
                }
            }
        } catch (IOException e) {
            System.err.println("\n✗ Erreur de connexion: " + e.getMessage());
            System.err.println("Assurez-vous que le serveur maître est démarré.");
        }
    }

    private static Socket connecterAuMaitreAvecAttente(String host, int port) throws IOException {
        int attenteMs = 2000;
        while (true) {
            try {
                return new Socket(host, port);
            } catch (IOException e) {
                System.out.println("⏳ Serveur maître indisponible, nouvelle tentative dans 2s...");
                try {
                    Thread.sleep(attenteMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static String attendreMessage(BufferedReader in, Socket socket) {
        try {
            socket.setSoTimeout(10000);
            return in.readLine();
        } catch (IOException e) {
            return null;
        } finally {
            try {
                socket.setSoTimeout(0);
            } catch (Exception ignored) {}
        }
    }
}
