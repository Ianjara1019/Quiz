package serveur.service;

import data.SimpleJson;
import data.StorageManager;

import java.util.*;

/**
 * Service de gestion des scores.
 * Encapsule la lecture, écriture et agrégation des scores via StorageManager.
 */
public class ScoreService {
    private final Map<String, Integer> scores = new HashMap<>();
    private final StorageManager storage;
    private final String section;       // "scores_global" ou clé de partition
    private final String partitionKey;  // null pour global, ex: "partition_0-33" pour partition

    /** Constructeur pour scores globaux. */
    public ScoreService(StorageManager storage) {
        this(storage, "scores_global", null);
    }

    /** Constructeur pour une partition de scores. */
    public ScoreService(StorageManager storage, String partitionKey) {
        this(storage, "scores_partitions", partitionKey);
    }

    private ScoreService(StorageManager storage, String section, String partitionKey) {
        this.storage = storage;
        this.section = section;
        this.partitionKey = partitionKey;
        charger();
    }

    public synchronized void ajouterScore(String nom, int points) {
        scores.put(nom, scores.getOrDefault(nom, 0) + points);
        sauvegarder();
    }

    public synchronized int getScore(String nom) {
        return scores.getOrDefault(nom, 0);
    }

    public synchronized Map<String, Integer> getTousLesScores() {
        return new HashMap<>(scores);
    }

    public synchronized void fusionnerMax(Map<String, Integer> source) {
        source.forEach((nom, score) ->
            scores.put(nom, Math.max(scores.getOrDefault(nom, 0), score))
        );
        sauvegarder();
    }

    public synchronized List<Map.Entry<String, Integer>> getClassement(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    public synchronized List<Map.Entry<String, Integer>> getClassement() {
        return getClassement(0);
    }

    // --- Persistance ---

    @SuppressWarnings("unchecked")
    private void charger() {
        Map<String, Object> map;
        if (partitionKey == null) {
            map = storage.getMap(section);
        } else {
            Map<String, Object> partitions = storage.getMap("scores_partitions");
            Object pObj = partitions.get(partitionKey);
            map = (pObj instanceof Map) ? (Map<String, Object>) pObj : new LinkedHashMap<>();
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            scores.put(e.getKey(), SimpleJson.toInt(e.getValue(), 0));
        }
        System.out.println("✓ " + scores.size() + " scores chargés"
            + (partitionKey != null ? " (partition " + partitionKey + ")" : " (global)"));
    }

    private void sauvegarder() {
        Map<String, Object> map = new LinkedHashMap<>();
        scores.forEach((nom, score) -> map.put(nom, score));
        if (partitionKey == null) {
            storage.sauvegarder(section, map);
        } else {
            storage.sauvegarderPartition(partitionKey, map);
        }
    }
}
