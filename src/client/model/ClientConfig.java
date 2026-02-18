package client.model;

public class ClientConfig {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6000;
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private final String host;
    private final int port;
    private final String token;
    private final int socketTimeoutMs;

    public ClientConfig(String host, int port, String token, int socketTimeoutMs) {
        this.host = host;
        this.port = port;
        this.token = token;
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public static ClientConfig fromEnvAndArgs(String[] args) {
        String host = DEFAULT_HOST;
        String envHost = System.getenv("QUIZ_SERVER_HOST");
        if (envHost != null && !envHost.isBlank()) {
            host = envHost.trim();
        }
        if (args != null && args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            host = args[0].trim();
        }

        String token = System.getenv("QUIZ_CLIENT_TOKEN");
        if (token != null && token.isBlank()) {
            token = null;
        }

        int timeoutMs = DEFAULT_TIMEOUT_MS;
        String envTimeout = System.getenv("QUIZ_SOCKET_TIMEOUT_MS");
        if (envTimeout != null && !envTimeout.isBlank()) {
            try {
                int parsed = Integer.parseInt(envTimeout.trim());
                if (parsed > 0) {
                    timeoutMs = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return new ClientConfig(host, DEFAULT_PORT, token, timeoutMs);
    }
}
