package serveur;

import data.MatchHistory;
import data.MatchHistory.PlayerScore;
import data.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public class Match {
    private final String theme;
    private final List<Question> questions;
    private final List<PlayerSession> joueurs;
    private final int nbQuestions;
    private final int manches;
    private final int roundTimerMs;
    private final BiConsumer<String, Integer> scoreRecorder;
    private final MatchHistory history;

    public Match(String theme, List<Question> questions, List<PlayerSession> joueurs,
                 int nbQuestions, int manches, int roundTimerMs,
                 BiConsumer<String, Integer> scoreRecorder,
                 MatchHistory history) {
        this.theme = theme;
        this.questions = questions;
        this.joueurs = joueurs;
        this.nbQuestions = nbQuestions;
        this.manches = manches;
        this.roundTimerMs = roundTimerMs;
        this.scoreRecorder = scoreRecorder;
        this.history = history;
    }

    public void jouer() {
        String matchId = "M" + System.currentTimeMillis() + "-" + new Random().nextInt(1000);
        broadcast("MATCH_START:ID=" + matchId + ";THEME=" + theme + ";PLAYERS=" + joueursListe()
            + ";ROUNDS=" + manches);

        for (int manche = 1; manche <= manches; manche++) {
            long roundDeadline = System.currentTimeMillis() + roundTimerMs;
            broadcast("ROUND_START:" + manche + "/" + manches + ";TIMER_MS=" + roundTimerMs);
            List<Question> qList = selectionQuestions();
            for (Question q : qList) {
                long remaining = roundDeadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                broadcast("QUESTION:" + q.getTexte());
                for (PlayerSession p : joueurs) {
                    try {
                        long left = roundDeadline - System.currentTimeMillis();
                        if (left <= 0) break;
                        int timeout = (int) Math.min(Integer.MAX_VALUE, left);
                        String rep = p.readLineWithTimeout(timeout);
                        if (rep != null && q.estCorrecte(rep)) {
                            p.addScore(10);
                        }
                    } catch (Exception e) {
                        // Pas de reponse ou timeout
                    }
                }
            }
            broadcast("ROUND_END:" + manche + "/" + manches);
        }

        List<PlayerSession> classement = new ArrayList<>(joueurs);
        classement.sort(Comparator.comparingInt(PlayerSession::getScore).reversed());

        int total = classement.size();
        List<PlayerScore> scores = new ArrayList<>();
        for (int i = 0; i < classement.size(); i++) {
            PlayerSession p = classement.get(i);
            int rank = i + 1;
            scores.add(new PlayerScore(p.getUsername(), p.getScore(), rank, total));
        }

        long ts = System.currentTimeMillis();
        history.enregistrerMatch(matchId, theme, ts, scores);

        for (PlayerSession p : joueurs) {
            int rank = getRankFor(p, scores);
            p.send("MATCH_END:Score=" + p.getScore() + ";Rang=" + rank + ";Total=" + total);
            scoreRecorder.accept(p.getUsername(), p.getScore());
            p.closeQuiet();
            p.terminer();
        }
    }

    private void broadcast(String msg) {
        for (PlayerSession p : joueurs) {
            p.send(msg);
        }
    }

    private String joueursListe() {
        List<String> noms = new ArrayList<>();
        for (PlayerSession p : joueurs) {
            noms.add(p.getUsername());
        }
        return String.join(",", noms);
    }

    private List<Question> selectionQuestions() {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Question> copie = new ArrayList<>(questions);
        Collections.shuffle(copie);
        int count = Math.min(nbQuestions, copie.size());
        return new ArrayList<>(copie.subList(0, count));
    }

    private int getRankFor(PlayerSession joueur, List<PlayerScore> scores) {
        for (PlayerScore ps : scores) {
            if (ps.username.equals(joueur.getUsername())) {
                return ps.rank;
            }
        }
        return scores.size();
    }
}
