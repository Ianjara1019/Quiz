package data;

import java.util.Locale;

/**
 * Représente une question de quiz avec difficulté et points.
 * Supporte la correspondance floue (distance de Levenshtein) pour tolérer les fautes.
 */
public class Question {
    private final String texte;
    private final String reponse;
    private final int difficulty;    // 1=facile, 2=moyen, 3=difficile
    private final int points;        // points de base

    /** Seuil de distance Levenshtein relative pour accepter une réponse proche. */
    private static final double LEVENSHTEIN_THRESHOLD = 0.25;

    public Question(String texte, String reponse) {
        this(texte, reponse, 1, 10);
    }

    public Question(String texte, String reponse, int difficulty, int points) {
        this.texte = texte;
        this.reponse = reponse;
        this.difficulty = Math.max(1, Math.min(3, difficulty));
        this.points = points > 0 ? points : 10;
    }

    public String getTexte() {
        return texte;
    }

    public String getReponse() {
        return reponse;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getPoints() {
        return points;
    }

    /**
     * Retourne les points pondérés par la difficulté.
     * Facile=x1, Moyen=x1.5, Difficile=x2
     */
    public int getPointsPonderes() {
        switch (difficulty) {
            case 2: return (int) (points * 1.5);
            case 3: return points * 2;
            default: return points;
        }
    }

    /**
     * Vérifie si la réponse est correcte (exacte OU floue).
     */
    public boolean estCorrecte(String rep) {
        if (rep == null) return false;
        String a = normaliser(reponse);
        String b = normaliser(rep);
        if (a.equals(b)) return true;
        // Correspondance floue
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return true;
        int dist = levenshtein(a, b);
        return (double) dist / maxLen <= LEVENSHTEIN_THRESHOLD;
    }

    /**
     * Vérifie uniquement la correspondance exacte (sans tolérance).
     */
    public boolean estCorrecteExacte(String rep) {
        if (rep == null) return false;
        return normaliser(reponse).equals(normaliser(rep));
    }

    // --- Helpers ---

    private static String normaliser(String s) {
        return s.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[àáâãäå]", "a")
            .replaceAll("[èéêë]", "e")
            .replaceAll("[ìíîï]", "i")
            .replaceAll("[òóôõö]", "o")
            .replaceAll("[ùúûü]", "u")
            .replaceAll("[ç]", "c")
            .replaceAll("[^a-z0-9 ]", "")
            .replaceAll("\\s+", " ");
    }

    /**
     * Calcul de la distance de Levenshtein entre deux chaînes.
     */
    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    @Override
    public String toString() {
        return String.format("[D%d/%dpts] %s", difficulty, getPointsPonderes(), texte);
    }
}