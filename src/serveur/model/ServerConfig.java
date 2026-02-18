package serveur.model;

/**
 * Configuration centralisée du serveur maître.
 * Toutes les constantes et paramètres d'environnement sont ici.
 */
public class ServerConfig {
    // --- Ports réseau ---
    private final int portClients;
    private final int portCoordination;

    // --- Timeouts ---
    private final int socketTimeoutMs;
    private final long heartbeatTimeoutMs;
    private final long aggregationIntervalMs;
    private final long heartbeatCheckIntervalMs;

    // --- Sécurité ---
    private final String secretPartage;
    private final String tokenClient;

    // --- Fichiers ---
    private final String fichierRegistre;
    private final String fichierThemes;
    private final String fichierScoresGlobal;

    private ServerConfig(Builder b) {
        this.portClients = b.portClients;
        this.portCoordination = b.portCoordination;
        this.socketTimeoutMs = b.socketTimeoutMs;
        this.heartbeatTimeoutMs = b.heartbeatTimeoutMs;
        this.aggregationIntervalMs = b.aggregationIntervalMs;
        this.heartbeatCheckIntervalMs = b.heartbeatCheckIntervalMs;
        this.secretPartage = b.secretPartage;
        this.tokenClient = b.tokenClient;
        this.fichierRegistre = b.fichierRegistre;
        this.fichierThemes = b.fichierThemes;
        this.fichierScoresGlobal = b.fichierScoresGlobal;
    }

    // --- Getters ---
    public int getPortClients()              { return portClients; }
    public int getPortCoordination()         { return portCoordination; }
    public int getSocketTimeoutMs()          { return socketTimeoutMs; }
    public long getHeartbeatTimeoutMs()      { return heartbeatTimeoutMs; }
    public long getAggregationIntervalMs()   { return aggregationIntervalMs; }
    public long getHeartbeatCheckIntervalMs() { return heartbeatCheckIntervalMs; }
    public String getSecretPartage()         { return secretPartage; }
    public String getTokenClient()           { return tokenClient; }
    public String getFichierRegistre()       { return fichierRegistre; }
    public String getFichierThemes()         { return fichierThemes; }
    public String getFichierScoresGlobal()   { return fichierScoresGlobal; }

    /**
     * Charge la configuration depuis les variables d'environnement + valeurs par défaut.
     */
    public static ServerConfig fromEnv() {
        return new Builder()
            .portClients(envInt("QUIZ_PORT_CLIENTS", 6000))
            .portCoordination(envInt("QUIZ_PORT_COORDINATION", 6001))
            .socketTimeoutMs(envInt("QUIZ_SOCKET_TIMEOUT_MS", 15000))
            .heartbeatTimeoutMs(envLong("QUIZ_HEARTBEAT_TIMEOUT_MS", 30000))
            .aggregationIntervalMs(envLong("QUIZ_AGGREGATION_INTERVAL_MS", 30000))
            .heartbeatCheckIntervalMs(envLong("QUIZ_HEARTBEAT_CHECK_MS", 5000))
            .secretPartage(envStr("QUIZ_SHARED_SECRET"))
            .tokenClient(envStr("QUIZ_CLIENT_TOKEN"))
            .fichierRegistre(envStr("QUIZ_REGISTRE_FILE", "data/registre_serveurs.txt"))
            .fichierThemes(resolveThemesFile())
            .fichierScoresGlobal(envStr("QUIZ_SCORES_GLOBAL_FILE", "data/scores_global.txt"))
            .build();
    }

    // --- Builder pattern ---
    public static class Builder {
        private int portClients = 6000;
        private int portCoordination = 6001;
        private int socketTimeoutMs = 15000;
        private long heartbeatTimeoutMs = 30000;
        private long aggregationIntervalMs = 30000;
        private long heartbeatCheckIntervalMs = 5000;
        private String secretPartage;
        private String tokenClient;
        private String fichierRegistre = "data/registre_serveurs.txt";
        private String fichierThemes = "data/themes.json";
        private String fichierScoresGlobal = "data/scores_global.txt";

        public Builder portClients(int v)              { this.portClients = v; return this; }
        public Builder portCoordination(int v)         { this.portCoordination = v; return this; }
        public Builder socketTimeoutMs(int v)          { this.socketTimeoutMs = v; return this; }
        public Builder heartbeatTimeoutMs(long v)      { this.heartbeatTimeoutMs = v; return this; }
        public Builder aggregationIntervalMs(long v)   { this.aggregationIntervalMs = v; return this; }
        public Builder heartbeatCheckIntervalMs(long v) { this.heartbeatCheckIntervalMs = v; return this; }
        public Builder secretPartage(String v)         { this.secretPartage = v; return this; }
        public Builder tokenClient(String v)           { this.tokenClient = v; return this; }
        public Builder fichierRegistre(String v)       { this.fichierRegistre = v; return this; }
        public Builder fichierThemes(String v)         { this.fichierThemes = v; return this; }
        public Builder fichierScoresGlobal(String v)   { this.fichierScoresGlobal = v; return this; }

        public ServerConfig build() { return new ServerConfig(this); }
    }

    // --- Helpers environnement ---
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

    private static long envLong(String key, long def) {
        String v = envStr(key);
        if (v == null) return def;
        try { return Long.parseLong(v); }
        catch (NumberFormatException e) { return def; }
    }

    private static String resolveThemesFile() {
        String env = envStr("QUIZ_THEMES_FILE");
        if (env != null) return env;
        java.io.File json = new java.io.File("data/themes.json");
        if (json.exists()) return "data/themes.json";
        return "data/themes.txt";
    }
}
