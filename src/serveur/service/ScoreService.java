package serveur.service;

import java.io.*;
import java.util.*;

/**
 * Service de gestion des scores.
 * Encapsule la lecture, écriture et agrégation des scores locaux et globaux.
 */
public class ScoreService {
    private final Map<String, Integer> scores = new HashMap<>();
    private final String fichierScores;

    public ScoreService(String fichierScores) {
        this.fichierScores = fichierScores;
        charger();
    }

    /**
     * Ajoute des points au score d'un joueur.
     */
    public synchronized void ajouterScore(String nom, int points) {
        scores.put(nom, scores.getOrDefault(nom, 0) + points);
        sauvegarder();
    }

    /**
     * Retourne le score d'un joueur donné.
     */
    public synchronized int getScore(String nom) {
        return scores.getOrDefault(nom, 0);
    }

    /**
     * Retourne une copie thread-safe de tous les scores.
     */
    public synchronized Map<String, Integer> getTousLesScores() {
        return new HashMap<>(scores);
    }

    /**
     * Fusionne les scores d'une source externe (agrégation).
     * Garde le maximum entre le score existant et le score reçu.
     */
    public synchronized void fusionnerMax(Map<String, Integer> source) {
        source.forEach((nom, score) ->
            scores.put(nom, Math.max(scores.getOrDefault(nom, 0), score))
        );
        sauvegarder();
    }

    /**
     * Retourne le classement trié (meilleur en premier), limité à N entrées.
     */
    public synchronized List<Map.Entry<String, Integer>> getClassement(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    /**
     * Retourne le classement complet trié.
     */
    public synchronized List<Map.Entry<String, Integer>> getClassement() {
        return getClassement(0);
    }

    // --- Persistance ---

    private void charger() {
        File f = new File(fichierScores);
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ligne;
            while ((ligne = br.readLine()) != null) {
                String[] parts = ligne.split(";");
                if (parts.length == 2) {
                    try {
                        scores.put(parts[0], Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            System.out.println("✓ " + scores.size() + " scores chargés depuis " + fichierScores);
        } catch (IOException e) {
            System.err.println("Erreur chargement scores: " + e.getMessage());
        }
    }

    private void sauvegarder() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fichierScores))) {
            scores.forEach((nom, score) -> pw.println(nom + ";" + score));
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde scores: " + e.getMessage());
        }
    }
}
