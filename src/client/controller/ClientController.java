package client.controller;

import client.model.AuthRequest;
import client.model.ClientConfig;
import client.view.ConsoleView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

public class ClientController {
    private final ClientConfig config;
    private final ConsoleView view;

    public ClientController(ClientConfig config, ConsoleView view) {
        this.config = config;
        this.view = view;
    }

    public void run() {
        view.printHeader();
        boolean continuer = true;
        while (continuer) {
            try {
                continuer = gererSession();
            } catch (IOException e) {
                view.showError("Erreur de connexion: " + e.getMessage());
                view.showInfo("Assurez-vous que le serveur maître est démarré.");
                continuer = false;
            }
        }
    }

    private boolean gererSession() throws IOException {
        try (Socket socketMaitre = connecterAuMaitreAvecAttente(config.getHost(), config.getPort())) {
            BufferedReader inMaitre = new BufferedReader(
                new InputStreamReader(socketMaitre.getInputStream()));
            PrintWriter outMaitre = new PrintWriter(
                socketMaitre.getOutputStream(), true);

            String message = attendreMessage(inMaitre, socketMaitre);
            if (message == null) {
                view.showInfo("✗ Le serveur maître n'est pas prêt. Nouvelle tentative...");
                return true;
            }

            if ("MODE?".equals(message)) {
                int choice = view.askMainMenuChoice();
                if (choice == 2) {
                    String user = view.askUsername();
                    outMaitre.println(avecToken("HISTORY:" + user));
                } else if (choice == 3) {
                    outMaitre.println(avecToken("LEADERBOARD"));
                } else if (choice == 4) {
                    outMaitre.println(avecToken("THEMES"));
                } else if (choice == 5) {
                    outMaitre.println("QUIT");
                    return false;
                } else {
                    String theme = view.askTheme();
                    outMaitre.println(avecToken("PLAY:" + theme));
                }
            }

            String redirection = inMaitre.readLine();
            if (redirection == null) {
                view.showInfo("\n✗ Le serveur maître a fermé la connexion.");
                return false;
            }

            if (redirection.startsWith("ERREUR:")) {
                view.showError(redirection.substring(7));
                return true;
            }

            if ("HISTORY_BEGIN".equals(redirection)) {
                List<String> lignes = view.readBlock(inMaitre, "HISTORY_END");
                view.showHistory(lignes, "Historique personnel");
                return view.askPlayAfterInfo();
            }

            if ("LEADERBOARD_BEGIN".equals(redirection)) {
                List<String> lignes = view.readBlock(inMaitre, "LEADERBOARD_END");
                view.showLeaderboard(lignes);
                return view.askPlayAfterInfo();
            }

            if ("THEMES_BEGIN".equals(redirection)) {
                List<String> lignes = view.readBlock(inMaitre, "THEMES_END");
                view.showThemes(lignes);
                return view.askPlayAfterInfo();
            }

            if (!redirection.startsWith("REDIRECT:")) {
                view.showError("Réponse inattendue du serveur");
                return true;
            }

            String[] serverInfo = redirection.substring(9).split(":");
            String host = serverInfo[0];
            int port = Integer.parseInt(serverInfo[1]);
            view.showInfo("→ Redirection vers " + host + ":" + port + "\n");

            gererSessionEsclave(host, port);

            return view.askReplay();
        }
    }

    private void gererSessionEsclave(String host, int port) throws IOException {
        try (Socket socketEsclave = new Socket(host, port)) {
            BufferedReader inEsclave = new BufferedReader(
                new InputStreamReader(socketEsclave.getInputStream()));
            PrintWriter outEsclave = new PrintWriter(
                socketEsclave.getOutputStream(), true);

            int questionNum = 1;
            boolean termine = false;

            while (!termine) {
                String msg = inEsclave.readLine();
                if (msg == null) {
                    break;
                }

                if ("AUTH?".equals(msg)) {
                    AuthRequest auth = view.askAuth();
                    if (auth.isRegister()) {
                        outEsclave.println("REGISTER:" + auth.getUsername() + ";PASS:" + auth.getPassword());
                    } else {
                        outEsclave.println("LOGIN:" + auth.getUsername() + ";PASS:" + auth.getPassword());
                    }
                } else if (msg.startsWith("ERREUR:AUTH")) {
                    view.showError(msg);
                    break;
                } else if (msg.startsWith("OK:AUTH")) {
                    // no-op
                } else if (msg.startsWith("MENU:")) {
                    view.showInfo(msg);
                    outEsclave.println("1");
                } else if ("ROOM?".equals(msg)) {
                    String code = view.askRoomCode();
                    outEsclave.println("ROOM:" + (code == null ? "" : code));
                } else if ("MODE?".equals(msg)) {
                    String mode = view.askSoloOrMulti();
                    outEsclave.println("MODE:" + mode);
                } else if ("SOLO_PRET".equals(msg)) {
                    view.showInfo("→ Partie solo prête ! La partie commence...");
                } else if (msg.startsWith("SOLO_START:")) {
                    view.showSoloStart(msg);
                } else if (msg.startsWith("SOLO_QUESTION:")) {
                    view.showSoloQuestion(msg);
                    outEsclave.println(view.askAnswer());
                } else if (msg.startsWith("SOLO_CORRECT:") || msg.startsWith("SOLO_WRONG:")) {
                    view.showSoloResult(msg);
                } else if (msg.startsWith("SOLO_END:")) {
                    view.showSoloEnd(msg);
                    termine = true;
                } else if ("HISTOIRE_BEGIN".equals(msg)) {
                    List<String> lignes = view.readBlock(inEsclave, "HISTOIRE_END");
                    view.showHistory(lignes, "Historique");
                    termine = true;
                } else if ("EN_ATTENTE".equals(msg)) {
                    view.showInfo("→ En attente d'autres joueurs...");
                } else if (msg.startsWith("MATCH_START:")) {
                    view.showMatchStart(msg);
                } else if (msg.startsWith("QUESTION:")) {
                    view.showQuestion(questionNum, msg.substring(9));
                    outEsclave.println(view.askAnswer());
                    questionNum++;
                } else if (msg.startsWith("ROUND_START:")) {
                    view.showInfo("\n" + msg);
                } else if (msg.startsWith("ROUND_END:")) {
                    view.showInfo(msg + "\n");
                } else if (msg.startsWith("MATCH_END:")) {
                    view.showInfo("\n" + msg);
                    termine = true;
                } else if (!msg.isBlank()) {
                    view.showInfo(msg);
                }
            }
        }
    }

    private Socket connecterAuMaitreAvecAttente(String host, int port) throws IOException {
        int attenteMs = 2000;
        while (true) {
            try {
                return new Socket(host, port);
            } catch (IOException e) {
                view.showInfo("⏳ Serveur maître indisponible, nouvelle tentative dans 2s...");
                try {
                    Thread.sleep(attenteMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private String attendreMessage(BufferedReader in, Socket socket) {
        try {
            socket.setSoTimeout(config.getSocketTimeoutMs());
            return in.readLine();
        } catch (SocketTimeoutException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            try {
                socket.setSoTimeout(0);
            } catch (Exception ignored) {
            }
        }
    }

    private String avecToken(String message) {
        String token = config.getToken();
        if (token == null || token.isBlank()) {
            return message;
        }
        if (message.contains(";TOKEN:")) {
            return message;
        }
        if (message.endsWith(";")) {
            return message + "TOKEN:" + token.trim();
        }
        return message + ";TOKEN:" + token.trim();
    }
}
