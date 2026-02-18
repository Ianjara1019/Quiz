package serveur.service;

import data.Question;
import data.MatchHistory;
import serveur.Match;
import serveur.PlayerSession;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Service de matchmaking.
 * Gère la file d'attente des joueurs et la création des matchs.
 */
public class MatchmakingService {
    private final Map<String, List<PlayerSession>> fileAttente = new HashMap<>();
    private final Object verrou = new Object();
    private final int minJoueurs;
    private final int maxJoueurs;

    public MatchmakingService(int minJoueurs, int maxJoueurs) {
        this.minJoueurs = minJoueurs;
        this.maxJoueurs = maxJoueurs;
    }

    /**
     * Ajoute un joueur à la file d'attente.
     */
    public void ajouterJoueur(PlayerSession session) {
        synchronized (verrou) {
            String key = buildKey(session);
            fileAttente.computeIfAbsent(key, k -> new ArrayList<>()).add(session);
        }
    }

    /**
     * Essaie de former un groupe pour un match.
     * @return un groupe de joueurs prêt, ou null si aucun match possible.
     */
    public List<PlayerSession> prendreGroupePourMatch() {
        synchronized (verrou) {
            String keyToUse = null;
            for (Map.Entry<String, List<PlayerSession>> entry : fileAttente.entrySet()) {
                List<PlayerSession> queue = entry.getValue();
                // Nettoyer les sessions fermées
                queue.removeIf(s -> !s.isActive());
                if (queue.size() >= minJoueurs) {
                    keyToUse = entry.getKey();
                    break;
                }
            }
            if (keyToUse == null) return null;

            List<PlayerSession> queue = fileAttente.get(keyToUse);
            int count = Math.min(maxJoueurs, queue.size());
            List<PlayerSession> group = new ArrayList<>(queue.subList(0, count));
            queue.subList(0, count).clear();
            if (queue.isEmpty()) {
                fileAttente.remove(keyToUse);
            }
            return group;
        }
    }

    /**
     * Retourne le nombre de joueurs en attente.
     */
    public int getNbEnAttente() {
        synchronized (verrou) {
            return fileAttente.values().stream().mapToInt(List::size).sum();
        }
    }

    /**
     * Crée et lance un match avec le groupe donné.
     */
    public void lancerMatch(List<PlayerSession> group, String theme,
                            List<Question> questions, int nbQuestions,
                            int manches, int roundTimerMs,
                            BiConsumer<String, Integer> scoreRecorder,
                            MatchHistory history) {
        Match match = new Match(theme, questions, group, nbQuestions,
            manches, roundTimerMs, scoreRecorder, history);
        new Thread(match::jouer, "Match-" + System.currentTimeMillis()).start();
    }

    private String buildKey(PlayerSession session) {
        String room = session.getRoomCode() == null ? "" : session.getRoomCode();
        return room;
    }
}
