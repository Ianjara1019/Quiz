package serveur;

import data.Scores;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Serveur Maître - Gère la distribution des tâches et l'agrégation des scores
 * Utilise un système de stockage distribué basé sur le hachage
 */
public class ServeurCentralDistribue {
    private static final int PORT_MAITRE = 6000;
    private static final int PORT_COORDINATION = 6001; // pour communiquer avec esclaves
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private static final long HEARTBEAT_TIMEOUT_MS = 30000;
    private RegistreServeurs registre;
    private Map<String, Integer> scoresGlobaux = new HashMap<>();
    private final String secretPartage;
    private final String tokenClient;

    public ServeurCentralDistribue() {
        registre = new RegistreServeurs("data/registre_serveurs.txt");
        secretPartage = chargerEnv("QUIZ_SHARED_SECRET");
        tokenClient = chargerEnv("QUIZ_CLIENT_TOKEN");
    }

    /**
     * Démarre le serveur maître
     */
    public void demarrer() {
        // Thread 1: Écoute les enregistrements de serveurs esclaves
        new Thread(this::ecouterEnregistrements).start();
        
        // Thread 2: Écoute les demandes clients (redirection)
        new Thread(this::ecouterClients).start();
        
        // Thread 3: Agrège périodiquement les scores
        new Thread(this::aggregerScoresPeriodiquement).start();
        
        // Thread 4: Désactive les serveurs silencieux
        new Thread(this::surveillerHeartbeats).start();

        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   SERVEUR MAÎTRE DÉMARRÉ              ║");
        System.out.println("║   Port coordination: " + PORT_COORDINATION + "            ║");
        System.out.println("║   Port clients: " + PORT_MAITRE + "                 ║");
        System.out.println("╚════════════════════════════════════════╝");
    }

    /**
     * Écoute les enregistrements des serveurs esclaves
     */
    private void ecouterEnregistrements() {
        try (ServerSocket server = new ServerSocket(PORT_COORDINATION)) {
            System.out.println("→ En attente d'enregistrements de serveurs esclaves...");
            
            while (true) {
                Socket socket = server.accept();
                new Thread(() -> gererEnregistrement(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur coordination: " + e.getMessage());
        }
    }

    /**
     * Gère l'enregistrement d'un serveur esclave
     */
    private void gererEnregistrement(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            
            String message = in.readLine();
            if (message == null || message.isBlank()) {
                out.println("ERREUR:Message vide");
                return;
            }
            
            if (message.startsWith("REGISTER:")) {
                // Format: REGISTER:id;host;port;theme;partitionDebut;partitionFin
                String[] parts = message.substring(9).split(";");
                int index = 0;
                if (parts.length >= 1 && parts[0].startsWith("token=")) {
                    String token = parts[0].substring(6);
                    if (!verifierSecret(token)) {
                        out.println("ERREUR:Auth");
                        return;
                    }
                    index = 1;
                } else if (secretPartage != null) {
                    out.println("ERREUR:Auth");
                    return;
                }

                if (parts.length - index < 6) {
                    out.println("ERREUR:Format register");
                    return;
                }

                String id = parts[index];
                String host = parts[index + 1];
                int port = Integer.parseInt(parts[index + 2]);
                String theme = parts[index + 3];
                int partDebut = Integer.parseInt(parts[index + 4]);
                int partFin = Integer.parseInt(parts[index + 5]);

                if (!validerId(id) || !validerHost(host) || !validerTheme(theme)) {
                    out.println("ERREUR:Données invalides");
                    return;
                }
                if (!validerPort(port) || partDebut < 0 || partFin < partDebut) {
                    out.println("ERREUR:Paramètres invalides");
                    return;
                }

                RegistreServeurs.InfoServeur serveur = new RegistreServeurs.InfoServeur(
                    id, host, port, theme, partDebut, partFin
                );
                
                registre.enregistrer(serveur);
                out.println("OK:REGISTERED");
                registre.afficherEtat();
            }
            else if (message.startsWith("HEARTBEAT:")) {
                // Signaux de vie des serveurs
                String payload = message.substring(10);
                String serveurId = payload;
                if (payload.startsWith("token=")) {
                    String[] parts = payload.split(";", 2);
                    String token = parts[0].substring(6);
                    if (!verifierSecret(token)) {
                        out.println("ERREUR:Auth");
                        return;
                    }
                    serveurId = parts.length > 1 ? parts[1] : "";
                } else if (secretPartage != null) {
                    out.println("ERREUR:Auth");
                    return;
                }
                if (!validerId(serveurId)) {
                    out.println("ERREUR:Id invalide");
                    return;
                }
                registre.mettreAJourHeartbeat(serveurId);
                out.println("OK:ALIVE");
            }
            else if (message.startsWith("SCORE:")) {
                // Réception d'un score: SCORE:nom;score;serveurId
                String[] parts = message.substring(6).split(";");
                int index = 0;
                if (parts.length >= 1 && parts[0].startsWith("token=")) {
                    String token = parts[0].substring(6);
                    if (!verifierSecret(token)) {
                        out.println("ERREUR:Auth");
                        return;
                    }
                    index = 1;
                } else if (secretPartage != null) {
                    out.println("ERREUR:Auth");
                    return;
                }
                if (parts.length - index < 3) {
                    out.println("ERREUR:Format score");
                    return;
                }

                String nom = parts[index];
                int score = Integer.parseInt(parts[index + 1]);
                String serveurId = parts[index + 2];
                if (!validerNom(nom) || !validerId(serveurId)) {
                    out.println("ERREUR:Données invalides");
                    return;
                }
                
                synchronized(scoresGlobaux) {
                    scoresGlobaux.put(nom, scoresGlobaux.getOrDefault(nom, 0) + score);
                }
                
                out.println("OK:SCORE_SAVED");
                System.out.println("✓ Score reçu: " + nom + " = " + score);
            }
            
        } catch (Exception e) {
            System.err.println("Erreur enregistrement: " + e.getMessage());
        }
    }

    /**
     * Écoute les demandes des clients et les redirige vers le bon serveur esclave
     */
    private void ecouterClients() {
        try (ServerSocket server = new ServerSocket(PORT_MAITRE)) {
            System.out.println("→ En attente de clients...");
            
            while (true) {
                Socket socket = server.accept();
                new Thread(() -> gererClient(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur clients: " + e.getMessage());
        }
    }

    /**
     * Gère une demande client et le redirige vers le serveur approprié
     */
    private void gererClient(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            
            // Demander le mode
            out.println("MODE?");
            String ligne = in.readLine();
            if (ligne == null || ligne.isBlank()) {
                out.println("ERREUR:Requête manquante");
                return;
            }

            if (ligne.startsWith("HISTORY:")) {
                String username = extraireUsernameHistory(ligne);
                String token = extraireTokenClient(ligne);
                if (!verifierTokenClient(token)) {
                    out.println("ERREUR:Auth");
                    return;
                }
                if (!validerNom(username)) {
                    out.println("ERREUR:Utilisateur invalide");
                    return;
                }
                envoyerHistoriqueGlobal(username, out);
                return;
            }
            if (ligne.startsWith("LEADERBOARD")) {
                String token = extraireTokenClient(ligne);
                if (!verifierTokenClient(token)) {
                    out.println("ERREUR:Auth");
                    return;
                }
                envoyerClassement(out);
                return;
            }
            if (ligne.startsWith("QUIT")) {
                out.println("BYE");
                return;
            }

            String theme = extraireTheme(ligne);
            String token = extraireTokenClient(ligne);
            if (!verifierTokenClient(token)) {
                out.println("ERREUR:Auth");
                return;
            }
            if (!validerTheme(theme)) {
                out.println("ERREUR:Thème invalide");
                return;
            }
            
            // Sélectionner le meilleur serveur pour ce thème
            RegistreServeurs.InfoServeur serveur = registre.selectionnerServeur(theme);
            
            if (serveur == null) {
                out.println("ERREUR:Aucun serveur disponible pour " + theme);
                System.out.println("✗ Aucun serveur pour theme=" + theme);
                return;
            }
            
            // Informer le client du serveur à utiliser
            out.println("REDIRECT:" + serveur.host + ":" + serveur.port);
            
            registre.incrementerCharge(serveur.id);
            System.out.println("→ Client redirigé vers " + serveur.id + 
                             " (charge=" + serveur.charge + ")");
            
            // Le serveur esclave décrementera la charge après la partie
            
        } catch (IOException e) {
            System.err.println("Erreur gestion client: " + e.getMessage());
        }
    }

    /**
     * Agrège les scores depuis tous les serveurs toutes les 30 secondes
     */
    private void aggregerScoresPeriodiquement() {
        while (true) {
            try {
                Thread.sleep(30000); // 30 secondes
                aggregerScores();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Récupère et agrège les scores de tous les serveurs esclaves
     */
    private void aggregerScores() {
        System.out.println("\n⟳ Agrégation des scores...");
        
        Map<String, Integer> scoresTemporaires = new HashMap<>();
        
        for (RegistreServeurs.InfoServeur serveur : registre.getTousLesServeurs()) {
            if (!serveur.actif) continue;
            
            try {
                // Demander les scores au serveur esclave
                Socket s = new Socket(serveur.host, serveur.port);
                s.setSoTimeout(SOCKET_TIMEOUT_MS);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream()));
                
                if (secretPartage != null) {
                    out.println("GET_SCORES;token=" + secretPartage);
                } else {
                    out.println("GET_SCORES");
                }
                
                String ligne;
                while ((ligne = in.readLine()) != null && !ligne.equals("END_SCORES")) {
                    String[] parts = ligne.split(";");
                    String nom = parts[0];
                    int score = Integer.parseInt(parts[1]);
                    
                    scoresTemporaires.put(nom, 
                        scoresTemporaires.getOrDefault(nom, 0) + score);
                }
                
                s.close();
                
            } catch (IOException e) {
                System.err.println("Erreur agrégation " + serveur.id + ": " + e.getMessage());
            }
        }
        
        // Fusionner avec les scores globaux
        synchronized(scoresGlobaux) {
            scoresTemporaires.forEach((nom, score) -> 
                scoresGlobaux.put(nom, Math.max(scoresGlobaux.getOrDefault(nom, 0), score))
            );
        }
        
        sauvegarderScoresGlobaux();
        afficherClassement();
    }

    private void envoyerHistoriqueGlobal(String username, PrintWriter out) {
        out.println("HISTORY_BEGIN");
        for (RegistreServeurs.InfoServeur serveur : registre.getTousLesServeurs()) {
            if (!serveur.actif) continue;
            try {
                Socket s = new Socket(serveur.host, serveur.port);
                s.setSoTimeout(SOCKET_TIMEOUT_MS);
                PrintWriter outS = new PrintWriter(s.getOutputStream(), true);
                BufferedReader inS = new BufferedReader(
                    new InputStreamReader(s.getInputStream()));

                if (secretPartage != null) {
                    outS.println("GET_HISTORY;USER=" + username + ";token=" + secretPartage);
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
                System.err.println("Erreur historique " + serveur.id + ": " + e.getMessage());
            }
        }
        out.println("HISTORY_END");
    }

    private void envoyerClassement(PrintWriter out) {
        out.println("LEADERBOARD_BEGIN");
        synchronized(scoresGlobaux) {
            scoresGlobaux.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .forEach(entry -> out.println(entry.getKey() + ";" + entry.getValue()));
        }
        out.println("LEADERBOARD_END");
    }

    /**
     * Sauvegarde les scores globaux
     */
    private void sauvegarderScoresGlobaux() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("data/scores_global.txt"))) {
            synchronized(scoresGlobaux) {
                scoresGlobaux.forEach((nom, score) -> pw.println(nom + ";" + score));
            }
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde scores: " + e.getMessage());
        }
    }

    /**
     * Affiche le classement global
     */
    private void afficherClassement() {
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║        CLASSEMENT GLOBAL               ║");
        System.out.println("╠════════════════════════════════════════╣");
        
        synchronized(scoresGlobaux) {
            scoresGlobaux.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .forEach(entry -> {
                    System.out.printf("║ %-25s %10d pts ║%n", 
                        entry.getKey(), entry.getValue());
                });
        }
        
        System.out.println("╚════════════════════════════════════════╝\n");
    }

    public static void main(String[] args) {
        ServeurCentralDistribue serveur = new ServeurCentralDistribue();
        serveur.demarrer();
        
        // Menu interactif
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n[1] État des serveurs  [2] Classement  [3] Quitter");
            String choix = sc.nextLine();
            
            switch (choix) {
                case "1":
                    serveur.registre.afficherEtat();
                    break;
                case "2":
                    serveur.afficherClassement();
                    break;
                case "3":
                    System.exit(0);
                    break;
            }
        }
    }

    /**
     * Surveille les heartbeats et désactive les serveurs silencieux
     */
    private void surveillerHeartbeats() {
        while (true) {
            try {
                Thread.sleep(5000);
                registre.desactiverServeursSilencieux(HEARTBEAT_TIMEOUT_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private String chargerEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private boolean verifierSecret(String token) {
        if (secretPartage == null) return true;
        return token != null && token.equals(secretPartage);
    }

    private boolean verifierTokenClient(String token) {
        if (tokenClient == null) return true;
        return token != null && token.equals(tokenClient);
    }

    private String extraireTheme(String ligne) {
        if (ligne.startsWith("THEME:")) {
            String payload = ligne.substring(6);
            String[] parts = payload.split(";TOKEN:", 2);
            return parts[0].trim();
        }
        if (ligne.startsWith("PLAY:")) {
            String payload = ligne.substring(5);
            String[] parts = payload.split(";TOKEN:", 2);
            return parts[0].trim();
        }
        return ligne.trim();
    }

    private String extraireUsernameHistory(String ligne) {
        if (!ligne.startsWith("HISTORY:")) return null;
        String payload = ligne.substring(8);
        String[] parts = payload.split(";TOKEN:", 2);
        return parts[0].trim();
    }

    private String extraireTokenClient(String ligne) {
        int idx = ligne.indexOf(";TOKEN:");
        if (idx == -1) return null;
        return ligne.substring(idx + 7).trim();
    }

    private boolean validerTheme(String theme) {
        if (theme == null) return false;
        String t = theme.trim();
        return t.length() >= 1 && t.length() <= 50 && t.matches("[\\p{L}0-9 _\\-]+");
    }

    private boolean validerNom(String nom) {
        if (nom == null) return false;
        String n = nom.trim();
        return n.length() >= 1 && n.length() <= 40 && n.matches("[\\p{L}0-9 _\\-]+");
    }

    private boolean validerId(String id) {
        if (id == null) return false;
        String v = id.trim();
        return v.length() >= 1 && v.length() <= 20 && v.matches("[A-Za-z0-9_\\-]+");
    }

    private boolean validerHost(String host) {
        if (host == null) return false;
        String h = host.trim();
        return h.length() >= 1 && h.length() <= 100;
    }

    private boolean validerPort(int port) {
        return port > 0 && port <= 65535;
    }
}
