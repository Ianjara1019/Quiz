package serveur.model;

/**
 * Configuration centralisée du serveur esclave (thème).
 */
public class SlaveConfig {
    private final String id;
    private final String theme;
    private final int port;
    private final int partitionDebut;
    private final int partitionFin;

    // --- Réseau ---
    private final String hostMaitre;
    private final int portMaitre;
    private final String hostPublic;
    private final int socketTimeoutMs;

    // --- Match ---
    private final int minJoueursMatch;
    private final int maxJoueursMatch;
    private final int nbQuestionsMatch;
    private final int soloNbQuestions;
    private final int roundTimerMs;
    private final int partitionMax;

    // --- Sécurité ---
    private final String secretPartage;

    // --- Fichiers ---
    private final String fichierStorage;
    private final String partitionKey;

    private SlaveConfig(Builder b) {
        this.id = b.id;
        this.theme = b.theme;
        this.port = b.port;
        this.partitionDebut = b.partitionDebut;
        this.partitionFin = b.partitionFin;
        this.hostMaitre = b.hostMaitre;
        this.portMaitre = b.portMaitre;
        this.hostPublic = b.hostPublic;
        this.socketTimeoutMs = b.socketTimeoutMs;
        this.minJoueursMatch = b.minJoueursMatch;
        this.maxJoueursMatch = b.maxJoueursMatch;
        this.nbQuestionsMatch = b.nbQuestionsMatch;
        this.soloNbQuestions = b.soloNbQuestions;
        this.roundTimerMs = b.roundTimerMs;
        this.partitionMax = b.partitionMax;
        this.secretPartage = b.secretPartage;
        this.fichierStorage = b.fichierStorage;
        this.partitionKey = "partition_" + b.partitionDebut + "-" + b.partitionFin;
    }

    // --- Getters ---
    public String getId()                { return id; }
    public String getTheme()             { return theme; }
    public int getPort()                 { return port; }
    public int getPartitionDebut()       { return partitionDebut; }
    public int getPartitionFin()         { return partitionFin; }
    public String getHostMaitre()        { return hostMaitre; }
    public int getPortMaitre()           { return portMaitre; }
    public String getHostPublic()        { return hostPublic; }
    public int getSocketTimeoutMs()      { return socketTimeoutMs; }
    public int getMinJoueursMatch()      { return minJoueursMatch; }
    public int getMaxJoueursMatch()      { return maxJoueursMatch; }
    public int getNbQuestionsMatch()     { return nbQuestionsMatch; }
    public int getSoloNbQuestions()      { return soloNbQuestions; }
    public int getRoundTimerMs()         { return roundTimerMs; }
    public int getPartitionMax()         { return partitionMax; }
    public String getSecretPartage()     { return secretPartage; }
    public String getFichierStorage()       { return fichierStorage; }
    public String getPartitionKey()          { return partitionKey; }

    /**
     * Construit la config depuis les arguments CLI + variables d'environnement.
     */
    public static SlaveConfig fromArgs(String[] args) {
        if (args.length < 5) {
            throw new IllegalArgumentException(
                "Usage: <id> <theme> <port> <partitionDebut> <partitionFin>");
        }

        String id = args[0];
        String theme = args[1];
        int port = Integer.parseInt(args[2]);
        int partDebut = Integer.parseInt(args[3]);
        int partFin = Integer.parseInt(args[4]);

        return new Builder(id, theme, port, partDebut, partFin)
            .hostMaitre(envStr("QUIZ_MASTER_HOST", "localhost"))
            .portMaitre(envInt("QUIZ_PORT_COORDINATION", 6001))
            .hostPublic(envStr("QUIZ_SERVER_HOST", "localhost"))
            .socketTimeoutMs(envInt("QUIZ_SOCKET_TIMEOUT_MS", 15000))
            .minJoueursMatch(envInt("QUIZ_MIN_PLAYERS", 2))
            .maxJoueursMatch(envInt("QUIZ_MAX_PLAYERS", 4))
            .nbQuestionsMatch(envInt("QUIZ_NB_QUESTIONS", 5))
            .soloNbQuestions(envInt("QUIZ_SOLO_NB_QUESTIONS", 10))
            .roundTimerMs(envInt("QUIZ_ROUND_TIMER_MS", 45000))
            .partitionMax(envInt("QUIZ_PARTITION_MAX", 100))
            .secretPartage(envStr("QUIZ_SHARED_SECRET"))
            .fichierStorage(envStr("QUIZ_STORAGE_FILE", "data/storage.json"))
            .build();
    }

    public static class Builder {
        private final String id;
        private final String theme;
        private final int port;
        private final int partitionDebut;
        private final int partitionFin;
        private String hostMaitre = "localhost";
        private int portMaitre = 6001;
        private String hostPublic = "localhost";
        private int socketTimeoutMs = 15000;
        private int minJoueursMatch = 2;
        private int maxJoueursMatch = 4;
        private int nbQuestionsMatch = 5;
        private int soloNbQuestions = 10;
        private int roundTimerMs = 45000;
        private int partitionMax = 100;
        private String secretPartage;
        private String fichierStorage = "data/storage.json";

        public Builder(String id, String theme, int port, int partDebut, int partFin) {
            this.id = id;
            this.theme = theme;
            this.port = port;
            this.partitionDebut = partDebut;
            this.partitionFin = partFin;
            this.fichierStorage = "data/storage.json";
        }

        public Builder hostMaitre(String v)        { this.hostMaitre = v; return this; }
        public Builder portMaitre(int v)           { this.portMaitre = v; return this; }
        public Builder hostPublic(String v)        { this.hostPublic = v; return this; }
        public Builder socketTimeoutMs(int v)      { this.socketTimeoutMs = v; return this; }
        public Builder minJoueursMatch(int v)      { this.minJoueursMatch = v; return this; }
        public Builder maxJoueursMatch(int v)      { this.maxJoueursMatch = v; return this; }
        public Builder nbQuestionsMatch(int v)     { this.nbQuestionsMatch = v; return this; }
        public Builder soloNbQuestions(int v)      { this.soloNbQuestions = v; return this; }
        public Builder roundTimerMs(int v)         { this.roundTimerMs = v; return this; }
        public Builder partitionMax(int v)         { this.partitionMax = v; return this; }
        public Builder secretPartage(String v)     { this.secretPartage = v; return this; }
        public Builder fichierStorage(String v)        { this.fichierStorage = v; return this; }

        public SlaveConfig build() { return new SlaveConfig(this); }
    }

    // --- Helpers ---
    private static String envStr(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String envStr(String key, String def) {
        String v = envStr(key);
        return v != null ? v : def;
    }

    private static int envInt(String key, int def) {
        String v = envStr(key);
        if (v == null) return def;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return def; }
    }

}
