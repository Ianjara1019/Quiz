package data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

public class AuthManager {
    private static final String LOGIN_PREFIX = "LOGIN:";
    private static final String REGISTER_PREFIX = "REGISTER:";
    private static final String PASS_PREFIX = "PASS:";
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StorageManager storage;
    private final Object verrou = new Object();

    public AuthManager(StorageManager storage) {
        this.storage = storage;
    }

    public static class Result {
        public final boolean ok;
        public final String username;
        public final String message;

        public Result(boolean ok, String username, String message) {
            this.ok = ok;
            this.username = username;
            this.message = message;
        }
    }

    private static class Credentials {
        private final String username;
        private final String password;

        private Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private static class UserRecord {
        private final String saltHex;
        private final String hashHex;

        private UserRecord(String saltHex, String hashHex) {
            this.saltHex = saltHex;
            this.hashHex = hashHex;
        }
    }

    public Result authentifier(String message) {
        if (message == null || message.isBlank()) {
            return new Result(false, null, "Message vide");
        }

        if (message.startsWith(LOGIN_PREFIX)) {
            return traiterLogin(message.substring(LOGIN_PREFIX.length()));
        }
        if (message.startsWith(REGISTER_PREFIX)) {
            return traiterRegister(message.substring(REGISTER_PREFIX.length()));
        }

        return new Result(false, null, "Commande inconnue");
    }

    private Result traiterLogin(String payload) {
        Credentials credentials = parseCredentials(payload);
        if (credentials == null) {
            return new Result(false, null, "Format login invalide");
        }
        if (!validerUsername(credentials.username)) {
            return new Result(false, null, "Username invalide");
        }
        return verifierLogin(credentials.username, credentials.password);
    }

    private Result traiterRegister(String payload) {
        Credentials credentials = parseCredentials(payload);
        if (credentials == null) {
            return new Result(false, null, "Format register invalide");
        }
        if (!validerUsername(credentials.username)) {
            return new Result(false, null, "Username invalide");
        }
        if (credentials.password.length() < MIN_PASSWORD_LENGTH) {
            return new Result(false, null, "Mot de passe trop court");
        }
        return enregistrer(credentials.username, credentials.password);
    }

    private Result verifierLogin(String username, String password) {
        synchronized (verrou) {
            Map<String, UserRecord> users = chargerUsers();
            UserRecord userRecord = users.get(username);
            if (userRecord == null) {
                return new Result(false, null, "Utilisateur inconnu");
            }

            String calc = sha256Hex(hexToBytes(userRecord.saltHex), password);
            if (!calc.equals(userRecord.hashHex)) {
                return new Result(false, null, "Mot de passe incorrect");
            }
            return new Result(true, username, "OK");
        }
    }

    private Result enregistrer(String username, String password) {
        synchronized (verrou) {
            Map<String, UserRecord> users = chargerUsers();
            if (users.containsKey(username)) {
                return new Result(false, null, "Utilisateur deja existant");
            }

            byte[] salt = new byte[SALT_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(salt);
            String saltHex = bytesToHex(salt);
            String hashHex = sha256Hex(salt, password);
            users.put(username, new UserRecord(saltHex, hashHex));
            sauvegarderUsers(users);
            return new Result(true, username, "OK");
        }
    }

    private Map<String, UserRecord> chargerUsers() {
        Map<String, UserRecord> users = new HashMap<>();
        List<Map<String, Object>> userList = storage.getList("users");
        for (Map<String, Object> u : userList) {
            String username = SimpleJson.toStr(u.get("username"), null);
            String salt = SimpleJson.toStr(u.get("salt"), null);
            String hash = SimpleJson.toStr(u.get("hash"), null);
            if (username != null && salt != null && hash != null) {
                users.put(username, new UserRecord(salt, hash));
            }
        }
        return users;
    }

    private void sauvegarderUsers(Map<String, UserRecord> users) {
        List<Map<String, Object>> userList = new ArrayList<>();
        for (Map.Entry<String, UserRecord> entry : users.entrySet()) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("username", entry.getKey());
            u.put("salt", entry.getValue().saltHex);
            u.put("hash", entry.getValue().hashHex);
            userList.add(u);
        }
        storage.sauvegarder("users", userList);
    }

    private Credentials parseCredentials(String payload) {
        String[] parts = payload.split(";");
        if (parts.length < 2) {
            return null;
        }

        String username = parts[0].trim();
        String password = null;
        for (String p : parts) {
            if (p.startsWith(PASS_PREFIX)) {
                password = p.substring(PASS_PREFIX.length());
                break;
            }
        }
        if (username.isEmpty() || password == null || password.isEmpty()) {
            return null;
        }
        return new Credentials(username, password);
    }

    private boolean validerUsername(String username) {
        if (username == null) return false;
        String u = username.trim();
        return u.length() >= 3 && u.length() <= 20 && u.matches("[A-Za-z0-9_\\-]+");
    }

    private String sha256Hex(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
