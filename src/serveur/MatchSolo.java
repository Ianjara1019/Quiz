package serveur;

import data.MatchHistory;
import data.MatchHistory.PlayerScore;
import data.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Gère une partie en mode solo (un seul joueur).
 *
 * <p>Différences avec {@link Match} multi-joueurs :
 * <ul>
 *   <li>Pas d'attente d'autres joueurs</li>
 *   <li>Retour immédiat de la bonne réponse après chaque question</li>
 *   <li>Statistiques détaillées en fin de partie (% réussite, temps moyen, meilleure série)</li>
 *   <li>Pas de timer global par manche — timer par question uniquement</li>
 * </ul>
 */
public class MatchSolo {

    /** Bonus de vitesse maximal (en % des points de base). */
    private static final double SPEED_BONUS_MAX = 0.5;
    /** Durée maximale par question pour le calcul du bonus (ms). */
    private static final long QUESTION_TIMER_MS = 15_000;

    private final String theme;
    private final List<Question> questions;
    private final PlayerSession joueur;
    private final int nbQuestions;
    private final BiConsumer<String, Integer> scoreRecorder;
    private final MatchHistory history;

    public MatchSolo(String theme, List<Question> questions, PlayerSession joueur,
                     int nbQuestions, BiConsumer<String, Integer> scoreRecorder,
                     MatchHistory history) {
        this.theme = theme;
        this.questions = questions;
        this.joueur = joueur;
        this.nbQuestions = nbQuestions;
        this.scoreRecorder = scoreRecorder;
        this.history = history;
    }

    public void jouer() {
        String matchId = "SOLO-" + System.currentTimeMillis() + "-" + new Random().nextInt(1000);
        List<Question> selection = selectionQuestions();
        int total = selection.size();

        joueur.send("SOLO_START:ID=" + matchId + ";THEME=" + theme + ";NB_QUESTIONS=" + total);

        int bonnes = 0;
        long tempsTotal = 0;
        int meilleureCombo = 0;
        int comboActuel = 0;

        for (int i = 0; i < selection.size(); i++) {
            Question q = selection.get(i);
            int basePoints = q.getPointsPonderes();
            String diffLabel = difficultyLabel(q.getDifficulty());

            joueur.send("SOLO_QUESTION:" + (i + 1) + "/" + total
                + ";[" + diffLabel + " +" + basePoints + "pts] " + q.getTexte());

            String rep = null;
            long elapsed = QUESTION_TIMER_MS;
            try {
                long t0 = System.currentTimeMillis();
                rep = joueur.readLineWithTimeout((int) QUESTION_TIMER_MS);
                elapsed = System.currentTimeMillis() - t0;
            } catch (Exception ignored) {
                // timeout
            }

            tempsTotal += elapsed;

            if (rep != null && q.estCorrecte(rep)) {
                int earned = calculerPoints(basePoints, elapsed, QUESTION_TIMER_MS);
                boolean exact = q.estCorrecteExacte(rep);
                joueur.addScore(earned);
                bonnes++;
                comboActuel++;
                meilleureCombo = Math.max(meilleureCombo, comboActuel);
                joueur.send("SOLO_CORRECT:" + (exact ? "EXACT" : "FUZZY")
                    + ";PTS=" + earned
                    + ";ELAPSED=" + elapsed + "ms"
                    + ";COMBO=" + comboActuel);
            } else {
                comboActuel = 0;
                joueur.send("SOLO_WRONG:ANSWER=" + q.getReponse());
            }
        }

        // --- Statistiques finales ---
        int scoreTotal = joueur.getScore();
        int pct = total > 0 ? (bonnes * 100 / total) : 0;
        long tempsMoyen = total > 0 ? (tempsTotal / total) : 0;
        String mention = getMention(pct);

        // Enregistrement dans l'historique
        List<PlayerScore> scores = new ArrayList<>();
        scores.add(new PlayerScore(joueur.getUsername(), scoreTotal, 1, 1));
        history.enregistrerMatch(matchId, theme, System.currentTimeMillis(), scores);
        scoreRecorder.accept(joueur.getUsername(), scoreTotal);

        joueur.send("SOLO_END:Score=" + scoreTotal
            + ";Bonnes=" + bonnes + "/" + total
            + ";Pct=" + pct
            + ";TempsMoyen=" + tempsMoyen + "ms"
            + ";MeilleureCombo=" + meilleureCombo
            + ";Mention=" + mention);

        joueur.closeQuiet();
        joueur.terminer();
    }

    /** Attribue une mention selon le pourcentage de bonnes réponses. */
    private static String getMention(int pct) {
        if (pct >= 90) return "EXCELLENT";
        if (pct >= 70) return "BIEN";
        if (pct >= 50) return "PASSABLE";
        return "A_AMELIORER";
    }

    private int calculerPoints(int basePoints, long elapsedMs, long maxMs) {
        if (maxMs <= 0) return basePoints;
        double ratio = 1.0 - ((double) elapsedMs / maxMs);
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        double bonus = basePoints * SPEED_BONUS_MAX * ratio;
        return basePoints + (int) Math.round(bonus);
    }

    private static String difficultyLabel(int d) {
        switch (d) {
            case 1: return "★";
            case 2: return "★★";
            case 3: return "★★★";
            default: return "★";
        }
    }

    private List<Question> selectionQuestions() {
        if (questions == null || questions.isEmpty()) return Collections.emptyList();
        List<Question> copie = new ArrayList<>(questions);
        Collections.shuffle(copie);
        int count = Math.min(nbQuestions, copie.size());
        return new ArrayList<>(copie.subList(0, count));
    }
}
