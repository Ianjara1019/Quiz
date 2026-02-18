package serveur;

import data.*;
import serveur.model.SlaveConfig;
import serveur.service.MatchmakingService;
import serveur.service.ProtocolParser;
import serveur.service.ScoreService;
import serveur.view.ConsoleLogger;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Serveur Esclave (Controller MVC) — Gère un thème, les matchs et une partition de scores.
 *
 * <p>Architecture :
 * <ul>
 *   <li><b>Model</b>  : {@link SlaveConfig}, {@link Themes}, {@link Question}</li>
 *   <li><b>View</b>   : {@link ConsoleLogger}</li>
 *   <li><b>Service</b> : {@link ScoreService}, {@link MatchmakingService}, {@link ProtocolParser}</li>
 * </ul>
 */
public class ServeurThemeDistribue {

    private final SlaveConfig config;
    private final Themes themes;
    private final ScoreService scoreService;
    private final MatchmakingService matchmaking;
    private final AuthManager authManager;
    private final MatchHistory matchHistory;
    private final ConsoleLogger log;

    // ────────────────────────────── Construction ──────────────────────────────

    public ServeurThemeDistribue(SlaveConfig config) {
        this.config = config;
        StorageManager storage = new StorageManager(config.getFichierStorage());
        this.themes = new Themes(storage);
        this.scoreService = new ScoreService(storage, config.getPartitionKey());
        this.matchmaking = new MatchmakingService(config.getMinJoueursMatch(), config.getMaxJoueursMatch());
        this.authManager = new AuthManager(storage);
        this.matchHistory = new MatchHistory(storage);
        this.log = new ConsoleLogger(config.getId());
    }

    /** Constructeur legacy pour compatibilité CLI directe. */
    public ServeurThemeDistribue(String id, String theme, int port,
                                  int partitionDebut, int partitionFin) {
        this(new SlaveConfig.Builder(id, theme, port, partitionDebut, partitionFin).build());
    }

    // ──────────────────────────── Démarrage ──────────────────────────────────

    public void demarrer() {
        if (!enregistrerAuMaitre()) {
            log.error("Impossible de s'enregistrer au serveur maître");
            return;
        }

        log.printBannerSlave(config.getId(), config.getTheme(), config.getPort(),
            config.getPartitionDebut(), config.getPartitionFin());

        new Thread(this::ecouterClients, config.getId() + "-Clients").start();
        new Thread(this::envoyerHeartbeats, config.getId() + "-Heartbeat").start();
        new Thread(this::matchmaker, config.getId() + "-Matchmaker").start();
    }

    // ──────────────────────── Enregistrement maître ────────────────────────

    private boolean enregistrerAuMaitre() {
        try {
            Socket socket = new Socket(config.getHostMaitre(), config.getPortMaitre());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            socket.setSoTimeout(config.getSocketTimeoutMs());

            String message;
            if (config.getSecretPartage() != null) {
                message = String.format("REGISTER:token=%s;%s;%s;%d;%s;%d;%d",
                    config.getSecretPartage(), config.getId(), config.getHostPublic(),
                    config.getPort(), config.getTheme(),
                    config.getPartitionDebut(), config.getPartitionFin());
            } else {
                message = String.format("REGISTER:%s;%s;%d;%s;%d;%d",
                    config.getId(), config.getHostPublic(), config.getPort(),
                    config.getTheme(), config.getPartitionDebut(), config.getPartitionFin());
            }

            out.println(message);
            String reponse = in.readLine();
            socket.close();

            if ("OK:REGISTERED".equals(reponse)) {
                log.success("Enregistrement au serveur maître réussi");
                return true;
            }
        } catch (IOException e) {
            log.error("Erreur enregistrement: " + e.getMessage());
        }
        return false;
    }

    // ──────────────────────────── Heartbeat ──────────────────────────────────

    private void envoyerHeartbeats() {
        while (true) {
            try {
                Thread.sleep(10000);
                Socket socket = new Socket(config.getHostMaitre(), config.getPortMaitre());
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                socket.setSoTimeout(config.getSocketTimeoutMs());

                if (config.getSecretPartage() != null) {
                    out.println("HEARTBEAT:token=" + config.getSecretPartage() + ";" + config.getId());
                } else {
                    out.println("HEARTBEAT:" + config.getId());
                }
                in.readLine();
                socket.close();
            } catch (Exception e) {
                log.error("Heartbeat échoué: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────── Gestion clients ─────────────────────────────

    private void ecouterClients() {
        try (ServerSocket server = new ServerSocket(config.getPort())) {
            log.waiting("Serveur " + config.getId() + " prêt sur le port " + config.getPort());
            while (true) {
                Socket client = server.accept();
                new Thread(() -> gererClient(client), config.getId() + "-ClientHandler").start();
            }
        } catch (IOException e) {
            log.error("Erreur serveur: " + e.getMessage());
        }
    }

    private void gererClient(Socket client) {
        try {
            client.setSoTimeout(config.getSocketTimeoutMs());
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);

            // Lire le premier message (peut être une commande interne)
            String premierMessage = null;
            try {
                client.setSoTimeout(2000);
                premierMessage = in.readLine();
            } catch (SocketTimeoutException ignored) {
            } finally {
                client.setSoTimeout(config.getSocketTimeoutMs());
            }

            // GET_SCORES — demande interne du maître
            if (premierMessage != null && premierMessage.startsWith("GET_SCORES")) {
                if (!ProtocolParser.verifierTokenServeur(premierMessage, config.getSecretPartage())) {
                    out.println("ERREUR:Auth");
                    client.close();
                    return;
                }
                envoyerScores(out);
                client.close();
                return;
            }

            // GET_HISTORY — demande interne du maître
            if (premierMessage != null && premierMessage.startsWith("GET_HISTORY")) {
                if (!ProtocolParser.verifierTokenServeur(premierMessage, config.getSecretPartage())) {
                    out.println("ERREUR:Auth");
                    client.close();
                    return;
                }
                String user = ProtocolParser.extraireUserHistory(premierMessage);
                out.println("HISTORY_BEGIN");
                if (user != null && !user.isBlank()) {
                    for (String l : matchHistory.getHistoriquePourUser(user.trim(), 200)) {
                        out.println(l);
                    }
                }
                out.println("HISTORY_END");
                client.close();
                return;
            }

            // Authentification client joueur
            out.println("AUTH?");
            String authMsg = premierMessage != null ? premierMessage : in.readLine();
            AuthManager.Result auth = authManager.authentifier(authMsg);
            if (!auth.ok) {
                out.println("ERREUR:AUTH:" + auth.message);
                client.close();
                return;
            }
            out.println("OK:AUTH");

            // Choix du mode : SOLO ou MULTI
            client.setSoTimeout(0);
            out.println("MODE?");
            String modeMsg = in.readLine();
            String mode = ProtocolParser.extraireMode(modeMsg);

            if ("SOLO".equals(mode)) {
                // Lancer une partie solo immédiatement
                List<Question> questions = themes.getQuestions(config.getTheme());
                PlayerSession session = new PlayerSession(
                    auth.username, null, client, in, out);
                out.println("SOLO_PRET");
                MatchSolo matchSolo = new MatchSolo(
                    config.getTheme(), questions, session,
                    config.getSoloNbQuestions(),
                    this::enregistrerScoreFinal, matchHistory);
                new Thread(matchSolo::jouer, "Solo-" + auth.username).start();
                session.attendreFinMatch();
            } else {
                // Mode multi-joueurs : salle privée + file d'attente
                out.println("ROOM?");
                String roomCode = ProtocolParser.extraireRoomCode(in.readLine());
                PlayerSession session = new PlayerSession(
                    auth.username, roomCode, client, in, out);
                out.println("EN_ATTENTE");
                matchmaking.ajouterJoueur(session);
                session.attendreFinMatch();
            }

        } catch (Exception e) {
            log.error("Erreur client: " + e.getMessage());
        }
    }

    // ──────────────────────────── Scores ────────────────────────────────────

    private void envoyerScoreAuMaitre(String nom, int score) {
        try {
            Socket socket = new Socket(config.getHostMaitre(), config.getPortMaitre());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            socket.setSoTimeout(config.getSocketTimeoutMs());

            if (config.getSecretPartage() != null) {
                out.println("SCORE:token=" + config.getSecretPartage() + ";" + nom + ";" + score + ";" + config.getId());
            } else {
                out.println("SCORE:" + nom + ";" + score + ";" + config.getId());
            }
            in.readLine();
            socket.close();
        } catch (IOException e) {
            log.error("Erreur envoi score: " + e.getMessage());
        }
    }

    private void envoyerScores(PrintWriter out) {
        Map<String, Integer> scores = scoreService.getTousLesScores();
        scores.forEach((nom, score) -> out.println(nom + ";" + score));
        out.println("END_SCORES");
    }

    private void enregistrerScoreFinal(String nom, int scoreFinal) {
        int hash = Math.abs(nom.hashCode() % config.getPartitionMax());
        if (hash >= config.getPartitionDebut() && hash <= config.getPartitionFin()) {
            scoreService.ajouterScore(nom, scoreFinal);
        } else {
            envoyerScoreAuMaitre(nom, scoreFinal);
        }
    }

    // ─────────────────────────── Matchmaking ────────────────────────────────

    private void matchmaker() {
        while (true) {
            try {
                Thread.sleep(1000);
                List<PlayerSession> group = matchmaking.prendreGroupePourMatch();
                if (group == null) continue;

                List<Question> questions = themes.getQuestions(config.getTheme());

                matchmaking.lancerMatch(group, config.getTheme(), questions,
                    config.getNbQuestionsMatch(), 1, config.getRoundTimerMs(),
                    this::enregistrerScoreFinal, matchHistory);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                log.error("Erreur matchmaking: " + e.getMessage());
            }
        }
    }

    // ──────────────────────────── Main ────────────────────────────────────────

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java ServeurThemeDistribue <id> <theme> <port> <partitionDebut> <partitionFin>");
            System.out.println("Exemple: java ServeurThemeDistribue S1 Maths 5001 0 33");
            return;
        }

        SlaveConfig config = SlaveConfig.fromArgs(args);
        ServeurThemeDistribue serveur = new ServeurThemeDistribue(config);
        serveur.demarrer();
    }
}
