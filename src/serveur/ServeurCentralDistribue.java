package serveur;

import data.Themes;
import serveur.model.ServerConfig;
import serveur.service.ProtocolParser;
import serveur.service.ScoreService;
import serveur.view.ConsoleLogger;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Serveur Maître (Controller MVC) — Gère la distribution et l'agrégation.
 *
 * <p>Architecture :
 * <ul>
 *   <li><b>Model</b>  : {@link ServerConfig}, {@link RegistreServeurs}, {@link ScoreService}</li>
 *   <li><b>View</b>   : {@link ConsoleLogger}</li>
 *   <li><b>Service</b> : {@link ProtocolParser}, {@link ScoreService}</li>
 * </ul>
 */
public class ServeurCentralDistribue {

    private final ServerConfig config;
    private final RegistreServeurs registre;
    private final ScoreService scoreService;
    private final Themes themes;
    private final ConsoleLogger log;

    // ────────────────────────────── Construction ──────────────────────────────

    public ServeurCentralDistribue() {
        this(ServerConfig.fromEnv());
    }

    public ServeurCentralDistribue(ServerConfig config) {
        this.config = config;
        this.registre = new RegistreServeurs(config.getFichierRegistre());
        this.scoreService = new ScoreService(config.getFichierScoresGlobal());
        this.themes = new Themes(config.getFichierThemes());
        this.log = new ConsoleLogger("MAITRE");
    }

    // ──────────────────────────── Démarrage ──────────────────────────────────

    public void demarrer() {
        new Thread(this::ecouterEnregistrements, "MasterCoord").start();
        new Thread(this::ecouterClients, "MasterClients").start();
        new Thread(this::aggregerScoresPeriodiquement, "MasterAggregation").start();
        new Thread(this::surveillerHeartbeats, "MasterHeartbeat").start();

        log.printBannerMaster(config.getPortCoordination(), config.getPortClients());
    }

    // ──────────────────────── Coordination esclaves ──────────────────────────

    private void ecouterEnregistrements() {
        try (ServerSocket server = new ServerSocket(config.getPortCoordination())) {
            log.waiting("En attente d'enregistrements de serveurs esclaves...");
            while (true) {
                Socket socket = server.accept();
                new Thread(() -> gererEnregistrement(socket), "SlaveReg").start();
            }
        } catch (IOException e) {
            log.error("Erreur serveur coordination: " + e.getMessage());
        }
    }

    private void gererEnregistrement(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            socket.setSoTimeout(config.getSocketTimeoutMs());
            String message = in.readLine();
            if (message == null || message.isBlank()) {
                out.println("ERREUR:Message vide");
                return;
            }

            if (message.startsWith("REGISTER:")) {
                traiterRegister(message, out);
            } else if (message.startsWith("HEARTBEAT:")) {
                traiterHeartbeat(message, out);
            } else if (message.startsWith("SCORE:")) {
                traiterScore(message, out);
            }

        } catch (Exception e) {
            log.error("Erreur enregistrement: " + e.getMessage());
        }
    }

    private void traiterRegister(String message, PrintWriter out) {
        String[] parts = message.substring(9).split(";");
        int index = 0;

        if (parts.length >= 1 && parts[0].startsWith("token=")) {
            if (!verifierSecret(parts[0].substring(6))) { out.println("ERREUR:Auth"); return; }
            index = 1;
        } else if (config.getSecretPartage() != null) {
            out.println("ERREUR:Auth"); return;
        }

        if (parts.length - index < 6) { out.println("ERREUR:Format register"); return; }

        String id = parts[index];
        String host = parts[index + 1];
        int port = Integer.parseInt(parts[index + 2]);
        String theme = parts[index + 3];
        int partDebut = Integer.parseInt(parts[index + 4]);
        int partFin = Integer.parseInt(parts[index + 5]);

        if (!ProtocolParser.validerId(id) || !ProtocolParser.validerHost(host)
                || !ProtocolParser.validerTheme(theme)
                || !ProtocolParser.validerPort(port) || partDebut < 0 || partFin < partDebut) {
            out.println("ERREUR:Données invalides"); return;
        }

        registre.enregistrer(new RegistreServeurs.InfoServeur(id, host, port, theme, partDebut, partFin));
        out.println("OK:REGISTERED");
        registre.afficherEtat();
    }

    private void traiterHeartbeat(String message, PrintWriter out) {
        String payload = message.substring(10);
        String serveurId = payload;

        if (payload.startsWith("token=")) {
            String[] parts = payload.split(";", 2);
            if (!verifierSecret(parts[0].substring(6))) { out.println("ERREUR:Auth"); return; }
            serveurId = parts.length > 1 ? parts[1] : "";
        } else if (config.getSecretPartage() != null) {
            out.println("ERREUR:Auth"); return;
        }

        if (!ProtocolParser.validerId(serveurId)) { out.println("ERREUR:Id invalide"); return; }
        registre.mettreAJourHeartbeat(serveurId);
        out.println("OK:ALIVE");
    }

    private void traiterScore(String message, PrintWriter out) {
        String[] parts = message.substring(6).split(";");
        int index = 0;

        if (parts.length >= 1 && parts[0].startsWith("token=")) {
            if (!verifierSecret(parts[0].substring(6))) { out.println("ERREUR:Auth"); return; }
            index = 1;
        } else if (config.getSecretPartage() != null) {
            out.println("ERREUR:Auth"); return;
        }

        if (parts.length - index < 3) { out.println("ERREUR:Format score"); return; }

        String nom = parts[index];
        int score = Integer.parseInt(parts[index + 1]);
        String serveurId = parts[index + 2];

        if (!ProtocolParser.validerNom(nom) || !ProtocolParser.validerId(serveurId)) {
            out.println("ERREUR:Données invalides"); return;
        }

        scoreService.ajouterScore(nom, score);
        out.println("OK:SCORE_SAVED");
        log.success("Score reçu: " + nom + " = " + score);
    }

    // ─────────────────────────── Gestion clients ─────────────────────────────

    private void ecouterClients() {
        try (ServerSocket server = new ServerSocket(config.getPortClients())) {
            log.waiting("En attente de clients...");
            while (true) {
                Socket socket = server.accept();
                new Thread(() -> gererClient(socket), "Client").start();
            }
        } catch (IOException e) {
            log.error("Erreur serveur clients: " + e.getMessage());
        }
    }

    private void gererClient(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            socket.setSoTimeout(config.getSocketTimeoutMs());
            out.println("MODE?");

            String ligne = in.readLine();
            if (ligne == null || ligne.isBlank()) {
                out.println("ERREUR:Requête manquante"); return;
            }

            if (ligne.startsWith("HISTORY:")) {
                if (!verifierTokenClient(ProtocolParser.extraireTokenClient(ligne))) {
                    out.println("ERREUR:Auth"); return;
                }
                String username = ProtocolParser.extraireUsernameHistory(ligne);
                if (!ProtocolParser.validerNom(username)) {
                    out.println("ERREUR:Utilisateur invalide"); return;
                }
                envoyerHistoriqueGlobal(username, out);
                return;
            }
            if (ligne.startsWith("LEADERBOARD")) {
                if (!verifierTokenClient(ProtocolParser.extraireTokenClient(ligne))) {
                    out.println("ERREUR:Auth"); return;
                }
                envoyerClassement(out);
                return;
            }
            if (ligne.startsWith("THEMES")) {
                if (!verifierTokenClient(ProtocolParser.extraireTokenClient(ligne))) {
                    out.println("ERREUR:Auth"); return;
                }
                envoyerThemes(out);
                return;
            }
            if (ligne.startsWith("QUIT")) { out.println("BYE"); return; }

            // Jouer
            String theme = ProtocolParser.extraireTheme(ligne);
            if (!verifierTokenClient(ProtocolParser.extraireTokenClient(ligne))) {
                out.println("ERREUR:Auth"); return;
            }
            if (!ProtocolParser.validerTheme(theme)) {
                out.println("ERREUR:Thème invalide"); return;
            }

            RegistreServeurs.InfoServeur serveur = registre.selectionnerServeur(theme);
            if (serveur == null) {
                out.println("ERREUR:Aucun serveur disponible pour " + theme);
                log.warn("Aucun serveur pour theme=" + theme);
                return;
            }

            out.println("REDIRECT:" + serveur.host + ":" + serveur.port);
            registre.incrementerCharge(serveur.id);
            log.info("Client redirigé vers " + serveur.id + " (charge=" + serveur.charge + ")");

        } catch (IOException e) {
            log.error("Erreur gestion client: " + e.getMessage());
        }
    }

    // ───────────────────────── Agrégation des scores ─────────────────────────

    private void aggregerScoresPeriodiquement() {
        while (true) {
            try {
                Thread.sleep(config.getAggregationIntervalMs());
                aggregerScores();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void aggregerScores() {
        log.aggregation("Agrégation des scores...");
        Map<String, Integer> scoresTemporaires = new HashMap<>();

        for (RegistreServeurs.InfoServeur serveur : registre.getTousLesServeurs()) {
            if (!serveur.actif) continue;
            try {
                Socket s = new Socket(serveur.host, serveur.port);
                s.setSoTimeout(config.getSocketTimeoutMs());
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                if (config.getSecretPartage() != null) {
                    out.println("GET_SCORES;token=" + config.getSecretPartage());
                } else {
                    out.println("GET_SCORES");
                }

                String ligne;
                while ((ligne = in.readLine()) != null && !"END_SCORES".equals(ligne)) {
                    String[] parts = ligne.split(";");
                    if (parts.length == 2) {
                        try {
                            scoresTemporaires.put(parts[0],
                                scoresTemporaires.getOrDefault(parts[0], 0) + Integer.parseInt(parts[1]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                s.close();
            } catch (IOException e) {
                log.error("Agrégation " + serveur.id + ": " + e.getMessage());
            }
        }

        scoreService.fusionnerMax(scoresTemporaires);
        log.printClassement(scoreService.getClassement(10), 10);
    }

    // ────────────────────────── Réponses spéciales ───────────────────────────

    private void envoyerHistoriqueGlobal(String username, PrintWriter out) {
        out.println("HISTORY_BEGIN");
        for (RegistreServeurs.InfoServeur serveur : registre.getTousLesServeurs()) {
            if (!serveur.actif) continue;
            try {
                Socket s = new Socket(serveur.host, serveur.port);
                s.setSoTimeout(config.getSocketTimeoutMs());
                PrintWriter outS = new PrintWriter(s.getOutputStream(), true);
                BufferedReader inS = new BufferedReader(new InputStreamReader(s.getInputStream()));

                if (config.getSecretPartage() != null) {
                    outS.println("GET_HISTORY;USER=" + username + ";token=" + config.getSecretPartage());
                } else {
                    outS.println("GET_HISTORY;USER=" + username);
                }

                String ligne;
                while ((ligne = inS.readLine()) != null && !"HISTORY_END".equals(ligne)) {
                    if (!"HISTORY_BEGIN".equals(ligne)) {
                        out.println(ligne);
                    }
                }
                s.close();
            } catch (IOException e) {
                log.error("Historique " + serveur.id + ": " + e.getMessage());
            }
        }
        out.println("HISTORY_END");
    }

    private void envoyerClassement(PrintWriter out) {
        out.println("LEADERBOARD_BEGIN");
        for (Map.Entry<String, Integer> entry : scoreService.getClassement()) {
            out.println(entry.getKey() + ";" + entry.getValue());
        }
        out.println("LEADERBOARD_END");
    }

    private void envoyerThemes(PrintWriter out) {
        out.println("THEMES_BEGIN");
        for (String t : themes.getThemeNames()) {
            out.println(t);
        }
        out.println("THEMES_END");
    }

    // ──────────────────────────── Heartbeat ──────────────────────────────────

    private void surveillerHeartbeats() {
        while (true) {
            try {
                Thread.sleep(config.getHeartbeatCheckIntervalMs());
                registre.desactiverServeursSilencieux(config.getHeartbeatTimeoutMs());
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // ──────────────────────────── Sécurité ───────────────────────────────────

    private boolean verifierSecret(String token) {
        if (config.getSecretPartage() == null) return true;
        return token != null && token.equals(config.getSecretPartage());
    }

    private boolean verifierTokenClient(String token) {
        if (config.getTokenClient() == null) return true;
        return token != null && token.equals(config.getTokenClient());
    }

    // ──────────────────────────── Main ────────────────────────────────────────

    public static void main(String[] args) {
        ServeurCentralDistribue serveur = new ServeurCentralDistribue();
        serveur.demarrer();

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n[1] État des serveurs  [2] Classement  [3] Statistiques  [4] Quitter");
            String choix = sc.nextLine();
            switch (choix) {
                case "1": serveur.registre.afficherEtat(); break;
                case "2": serveur.log.printClassement(serveur.scoreService.getClassement(10), 10); break;
                case "3":
                    System.out.println("Serveurs enregistrés: " + serveur.registre.getTousLesServeurs().size());
                    System.out.println("Joueurs scorés: " + serveur.scoreService.getTousLesScores().size());
                    break;
                case "4": System.exit(0); break;
            }
        }
    }
}
