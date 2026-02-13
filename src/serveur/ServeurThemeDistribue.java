package serveur;

import data.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Serveur Esclave (Thème) avec stockage distribué des scores
 * S'enregistre auprès du serveur maître et gère une partition des scores
 */
public class ServeurThemeDistribue {
    private String id;
    private String theme;
    private int port;
    private int partitionDebut;
    private int partitionFin;
    
    private Themes themes;
    private Map<String, Integer> scoresLocaux = new HashMap<>();
    private String fichierScores;
    
    private static final String HOST_MAITRE = "localhost";
    private static final int PORT_MAITRE = 6001;
    private static final String HOST_PUBLIC = "localhost";
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private final int partitionMax;
    private final String secretPartage;
    private static final int MIN_JOUEURS_MATCH = 2;
    private static final int MAX_JOUEURS_MATCH = 4;
    private static final int NB_QUESTIONS_MATCH = 5;
    private static final int TOURNOI_MANCHEES = 3;
    private static final int ROUND_TIMER_MS = 45000;

    private final Map<String, List<PlayerSession>> fileAttente = new HashMap<>();
    private final Object verrouFileAttente = new Object();
    private final data.AuthManager authManager;
    private final data.MatchHistory matchHistory;

    public ServeurThemeDistribue(String id, String theme, int port, 
                                  int partitionDebut, int partitionFin) {
        this.id = id;
        this.theme = theme;
        this.port = port;
        this.partitionDebut = partitionDebut;
        this.partitionFin = partitionFin;
        this.fichierScores = "data/scores_partition_" + partitionDebut + "-" + partitionFin + ".txt";
        
        this.themes = new Themes(resoudreThemesFile());
        chargerScores();
        this.partitionMax = chargerPartitionMax();
        this.secretPartage = chargerEnv("QUIZ_SHARED_SECRET");
        this.authManager = new data.AuthManager("data/users.txt");
        this.matchHistory = new data.MatchHistory("data/scores_matches.txt");
    }

    /**
     * Démarre le serveur esclave
     */
    public void demarrer() {
        // S'enregistrer auprès du serveur maître
        if (!enregistrerAuMaitre()) {
            System.err.println("✗ Impossible de s'enregistrer au serveur maître");
            return;
        }

        System.out.println("╔════════════════════════════════════════╗");
        System.out.printf("║ SERVEUR ESCLAVE: %-20s ║%n", id);
        System.out.printf("║ Thème: %-30s ║%n", theme);
        System.out.printf("║ Port: %-32d ║%n", port);
        System.out.printf("║ Partition scores: %d-%d              ║%n", partitionDebut, partitionFin);
        System.out.println("╚════════════════════════════════════════╝");

        // Thread 1: Écoute les clients pour les quiz
        new Thread(this::ecouterClients).start();
        
        // Thread 2: Envoie des heartbeats au maître
        new Thread(this::envoyerHeartbeats).start();

        // Thread 3: Matchmaking multi-joueurs
        new Thread(this::matchmaker).start();
    }

    /**
     * Enregistre ce serveur auprès du serveur maître
     */
    private boolean enregistrerAuMaitre() {
        try {
            Socket socket = new Socket(HOST_MAITRE, PORT_MAITRE);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            // Format: REGISTER:id;host;port;theme;partitionDebut;partitionFin
            String hostPublic = HOST_PUBLIC;
            String envHost = System.getenv("QUIZ_SERVER_HOST");
            if (envHost != null && !envHost.isBlank()) {
                hostPublic = envHost.trim();
            }
            String message;
            if (secretPartage != null) {
                message = String.format("REGISTER:token=%s;%s;%s;%d;%s;%d;%d",
                    secretPartage, id, hostPublic, port, theme, partitionDebut, partitionFin);
            } else {
                message = String.format("REGISTER:%s;%s;%d;%s;%d;%d",
                    id, hostPublic, port, theme, partitionDebut, partitionFin);
            }
            
            out.println(message);
            String reponse = in.readLine();
            
            socket.close();
            
            if ("OK:REGISTERED".equals(reponse)) {
                System.out.println("✓ Enregistrement au serveur maître réussi");
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("✗ Erreur enregistrement: " + e.getMessage());
        }
        return false;
    }

    /**
     * Envoie régulièrement des signaux de vie au serveur maître
     */
    private void envoyerHeartbeats() {
        while (true) {
            try {
                Thread.sleep(10000); // Toutes les 10 secondes
                
                Socket socket = new Socket(HOST_MAITRE, PORT_MAITRE);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                
                if (secretPartage != null) {
                    out.println("HEARTBEAT:token=" + secretPartage + ";" + id);
                } else {
                    out.println("HEARTBEAT:" + id);
                }
                in.readLine();
                socket.close();
                
            } catch (Exception e) {
                System.err.println("✗ Heartbeat échoué: " + e.getMessage());
            }
        }
    }

    /**
     * Écoute les connexions clients
     */
    private void ecouterClients() {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("→ Serveur " + id + " prêt sur le port " + port);
            
            while (true) {
                Socket client = server.accept();
                new Thread(() -> gererClient(client)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur: " + e.getMessage());
        }
    }

    /**
     * Gère une session de quiz avec un client
     */
    private void gererClient(Socket client) {
        try {
            client.setSoTimeout(SOCKET_TIMEOUT_MS);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(
                client.getOutputStream(), true);

            // Vérifier si c'est une demande de scores (du serveur maître)
            String premierMessage = null;
            try {
                client.setSoTimeout(2000);
                premierMessage = in.readLine();
            } catch (SocketTimeoutException ignored) {
                premierMessage = null;
            } finally {
                client.setSoTimeout(SOCKET_TIMEOUT_MS);
            }
            
            if (premierMessage != null && premierMessage.startsWith("GET_SCORES")) {
                if (!autoriserGetScores(premierMessage)) {
                    out.println("ERREUR:Auth");
                    client.close();
                    return;
                }
                envoyerScores(out);
                client.close();
                return;
            }
            if (premierMessage != null && premierMessage.startsWith("GET_HISTORY")) {
                if (!autoriserGetHistory(premierMessage)) {
                    out.println("ERREUR:Auth");
                    client.close();
                    return;
                }
                String user = extraireUserHistory(premierMessage);
                out.println("HISTORY_BEGIN");
                if (user != null && !user.isBlank()) {
                    List<String> lignes = matchHistory.getHistoriquePourUser(user.trim(), 200);
                    for (String l : lignes) {
                        out.println(l);
                    }
                }
                out.println("HISTORY_END");
                client.close();
                return;
            }

            // Authentification client
            out.println("AUTH?");
            String authMsg = premierMessage != null ? premierMessage : in.readLine();
            data.AuthManager.Result auth = authManager.authentifier(authMsg);
            if (!auth.ok) {
                out.println("ERREUR:AUTH:" + auth.message);
                client.close();
                return;
            }
            out.println("OK:AUTH");

            // Par défaut: jouer
            client.setSoTimeout(0);
            out.println("ROOM?");
            String roomMsg = in.readLine();
            String roomCode = extraireRoomCode(roomMsg);
            out.println("TOUR?");
            String tourMsg = in.readLine();
            boolean tournoi = extraireTournoi(tourMsg);
            PlayerSession session = new PlayerSession(auth.username, roomCode, tournoi, client, in, out);
            out.println("EN_ATTENTE");
            ajouterAFileAttente(session);
            session.attendreFinMatch();

        } catch (Exception e) {
            System.err.println("Erreur client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sauvegarde un score localement (dans la partition de ce serveur)
     */
    private void sauvegarderScoreLocal(String nom, int score) {
        synchronized(scoresLocaux) {
            scoresLocaux.put(nom, scoresLocaux.getOrDefault(nom, 0) + score);
            sauvegarderScores();
        }
    }

    /**
     * Envoie un score au serveur maître
     */
    private void envoyerScoreAuMaitre(String nom, int score) {
        try {
            Socket socket = new Socket(HOST_MAITRE, PORT_MAITRE);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            
            if (secretPartage != null) {
                out.println("SCORE:token=" + secretPartage + ";" + nom + ";" + score + ";" + id);
            } else {
                out.println("SCORE:" + nom + ";" + score + ";" + id);
            }
            in.readLine();
            socket.close();
            
        } catch (IOException e) {
            System.err.println("✗ Erreur envoi score: " + e.getMessage());
        }
    }

    /**
     * Envoie tous les scores locaux (pour agrégation par le maître)
     */
    private void envoyerScores(PrintWriter out) {
        synchronized(scoresLocaux) {
            scoresLocaux.forEach((nom, score) -> 
                out.println(nom + ";" + score)
            );
        }
        out.println("END_SCORES");
    }

    private boolean autoriserGetScores(String message) {
        if (secretPartage == null) return true;
        String[] parts = message.split(";", 2);
        if (parts.length < 2) return false;
        String tokenPart = parts[1].trim();
        return tokenPart.equals("token=" + secretPartage);
    }

    private boolean autoriserGetHistory(String message) {
        if (secretPartage == null) return true;
        String[] parts = message.split(";");
        for (String p : parts) {
            if (p.startsWith("token=")) {
                return p.equals("token=" + secretPartage);
            }
        }
        return false;
    }

    private String extraireUserHistory(String message) {
        String[] parts = message.split(";");
        for (String p : parts) {
            if (p.startsWith("USER=")) {
                return p.substring(5);
            }
        }
        return null;
    }

    private String chargerEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private int chargerPartitionMax() {
        String value = System.getenv("QUIZ_PARTITION_MAX");
        if (value == null || value.isBlank()) {
            return 100;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 100;
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private void ajouterAFileAttente(PlayerSession session) {
        synchronized (verrouFileAttente) {
            String key = session.getRoomCode() == null ? "" : session.getRoomCode();
            key = key + "|" + (session.isTournoi() ? "T" : "N");
            fileAttente.computeIfAbsent(key, k -> new ArrayList<>()).add(session);
        }
    }

    private List<PlayerSession> prendrePourMatch() {
        synchronized (verrouFileAttente) {
            String keyToUse = null;
            for (Map.Entry<String, List<PlayerSession>> entry : fileAttente.entrySet()) {
                List<PlayerSession> queue = entry.getValue();
                queue.removeIf(s -> !s.isActive());
                if (queue.size() < MIN_JOUEURS_MATCH) continue;
                keyToUse = entry.getKey();
                break;
            }
            if (keyToUse == null) return null;

            List<PlayerSession> queue = fileAttente.get(keyToUse);
            int count = Math.min(MAX_JOUEURS_MATCH, queue.size());
            List<PlayerSession> group = new ArrayList<>(queue.subList(0, count));
            queue.subList(0, count).clear();
            if (queue.isEmpty()) {
                fileAttente.remove(keyToUse);
            }
            return group;
        }
    }

    private void matchmaker() {
        while (true) {
            try {
                Thread.sleep(1000);
                List<PlayerSession> group = prendrePourMatch();
                if (group == null) continue;
                List<Question> questions = themes.getQuestions(theme);
                boolean tournoi = group.get(0).isTournoi();
                int manches = tournoi ? chargerTournoiManches() : 1;
                int roundTimerMs = chargerRoundTimerMs();
                Match match = new Match(theme, questions, group, NB_QUESTIONS_MATCH,
                    manches, roundTimerMs, this::enregistrerScoreFinal, matchHistory);
                new Thread(match::jouer).start();
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Erreur matchmaking: " + e.getMessage());
            }
        }
    }

    private void enregistrerScoreFinal(String nom, int scoreFinal) {
        int hash = Math.abs(nom.hashCode() % partitionMax);
        if (hash >= partitionDebut && hash <= partitionFin) {
            sauvegarderScoreLocal(nom, scoreFinal);
        } else {
            envoyerScoreAuMaitre(nom, scoreFinal);
        }
    }

    private String extraireRoomCode(String roomMsg) {
        if (roomMsg == null) return null;
        String msg = roomMsg.trim();
        if (msg.startsWith("ROOM:")) {
            String code = msg.substring(5).trim();
            if (code.isEmpty()) return null;
            return code;
        }
        if (msg.isEmpty()) return null;
        return msg;
    }

    private boolean extraireTournoi(String msg) {
        if (msg == null) return false;
        String v = msg.trim();
        if (v.startsWith("TOUR:")) {
            v = v.substring(5).trim();
        }
        return v.equalsIgnoreCase("o") || v.equalsIgnoreCase("oui") || v.equalsIgnoreCase("y");
    }

    private String resoudreThemesFile() {
        String env = System.getenv("QUIZ_THEMES_FILE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        File json = new File("data/themes.json");
        if (json.exists()) return "data/themes.json";
        return "data/themes.txt";
    }

    private int chargerTournoiManches() {
        String value = System.getenv("QUIZ_TOURNAMENT_ROUNDS");
        if (value == null || value.isBlank()) return TOURNOI_MANCHEES;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : TOURNOI_MANCHEES;
        } catch (NumberFormatException e) {
            return TOURNOI_MANCHEES;
        }
    }

    private int chargerRoundTimerMs() {
        String value = System.getenv("QUIZ_ROUND_TIMER_MS");
        if (value == null || value.isBlank()) return ROUND_TIMER_MS;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : ROUND_TIMER_MS;
        } catch (NumberFormatException e) {
            return ROUND_TIMER_MS;
        }
    }


    /**
     * Charge les scores depuis le fichier local
     */
    private void chargerScores() {
        File f = new File(fichierScores);
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ligne;
            while ((ligne = br.readLine()) != null) {
                String[] parts = ligne.split(";");
                if (parts.length == 2) {
                    scoresLocaux.put(parts[0], Integer.parseInt(parts[1]));
                }
            }
            System.out.println("✓ " + scoresLocaux.size() + " scores chargés");
        } catch (IOException e) {
            System.err.println("Erreur chargement scores: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde les scores dans le fichier local
     */
    private void sauvegarderScores() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fichierScores))) {
            scoresLocaux.forEach((nom, score) -> pw.println(nom + ";" + score));
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde scores: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java ServeurThemeDistribue <id> <theme> <port> <partitionDebut> <partitionFin>");
            System.out.println("Exemple: java ServeurThemeDistribue S1 Maths 5001 0 33");
            return;
        }

        String id = args[0];
        String theme = args[1];
        int port = Integer.parseInt(args[2]);
        int partitionDebut = Integer.parseInt(args[3]);
        int partitionFin = Integer.parseInt(args[4]);

        ServeurThemeDistribue serveur = new ServeurThemeDistribue(
            id, theme, port, partitionDebut, partitionFin
        );
        serveur.demarrer();
    }
}
