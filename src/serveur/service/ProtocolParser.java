package serveur.service;

/**
 * Utilitaire de parsing et de validation du protocole réseau.
 * Centralise toute la logique d'extraction des messages.
 */
public class ProtocolParser {

    private ProtocolParser() { /* utilitaire statique */ }

    // --- Validation ---

    public static boolean validerTheme(String theme) {
        if (theme == null) return false;
        String t = theme.trim();
        return t.length() >= 1 && t.length() <= 50 && t.matches("[\\p{L}0-9 _\\-]+");
    }

    public static boolean validerNom(String nom) {
        if (nom == null) return false;
        String n = nom.trim();
        return n.length() >= 1 && n.length() <= 40 && n.matches("[\\p{L}0-9 _\\-]+");
    }

    public static boolean validerId(String id) {
        if (id == null) return false;
        String v = id.trim();
        return v.length() >= 1 && v.length() <= 20 && v.matches("[A-Za-z0-9_\\-]+");
    }

    public static boolean validerHost(String host) {
        if (host == null) return false;
        String h = host.trim();
        return h.length() >= 1 && h.length() <= 100;
    }

    public static boolean validerPort(int port) {
        return port > 0 && port <= 65535;
    }

    // --- Extraction ---

    /**
     * Extrait le thème d'un message client.
     * Supporte: "Maths", "PLAY:Maths", "THEME:Maths", avec token optionnel.
     */
    public static String extraireTheme(String ligne) {
        if (ligne == null) return null;
        String payload = ligne.trim();
        if (payload.startsWith("PLAY:")) {
            payload = payload.substring(5);
        } else if (payload.startsWith("THEME:")) {
            payload = payload.substring(6);
        }
        return supprimerToken(payload).trim();
    }

    /**
     * Extrait le username d'un message HISTORY.
     */
    public static String extraireUsernameHistory(String ligne) {
        if (ligne == null || !ligne.startsWith("HISTORY:")) return null;
        String payload = ligne.substring(8);
        return supprimerToken(payload).trim();
    }

    /**
     * Extrait le token client (;TOKEN:xxx) d'un message quelconque.
     */
    public static String extraireTokenClient(String ligne) {
        if (ligne == null) return null;
        int idx = ligne.indexOf(";TOKEN:");
        if (idx == -1) return null;
        return ligne.substring(idx + 7).trim();
    }

    /**
     * Extrait le code de room d'un message ROOM:xxx.
     */
    public static String extraireRoomCode(String roomMsg) {
        if (roomMsg == null) return null;
        String msg = roomMsg.trim();
        if (msg.startsWith("ROOM:")) {
            String code = msg.substring(5).trim();
            return code.isEmpty() ? null : code;
        }
        return msg.isEmpty() ? null : msg;
    }

    /**
     * Extrait le mode de jeu d'un message MODE:xxx.
     * Retourne "SOLO" ou "MULTI" (défaut: "MULTI").
     */
    public static String extraireMode(String msg) {
        if (msg == null) return "MULTI";
        String v = msg.trim();
        if (v.startsWith("MODE:")) {
            v = v.substring(5).trim().toUpperCase();
        }
        return "SOLO".equals(v) ? "SOLO" : "MULTI";
    }

    /**
     * Extrait le USER= d'un message GET_HISTORY.
     */
    public static String extraireUserHistory(String message) {
        if (message == null) return null;
        String[] parts = message.split(";");
        for (String p : parts) {
            if (p.startsWith("USER=")) {
                return p.substring(5);
            }
        }
        return null;
    }

    /**
     * Vérifie le token dans un message serveur-serveur (;token=xxx).
     */
    public static boolean verifierTokenServeur(String message, String secret) {
        if (secret == null) return true;
        if (message == null) return false;
        String[] parts = message.split(";");
        for (String p : parts) {
            if (p.startsWith("token=")) {
                return p.equals("token=" + secret);
            }
        }
        return false;
    }

    // --- Helpers ---

    private static String supprimerToken(String payload) {
        int idx = payload.indexOf(";TOKEN:");
        if (idx == -1) return payload;
        return payload.substring(0, idx);
    }
}
