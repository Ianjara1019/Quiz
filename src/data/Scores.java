package data;

import java.util.*;

public class Scores {
    private Map<String, Integer> scores = new HashMap<>();
    private final StorageManager storage;

    public Scores(StorageManager storage) {
        this.storage = storage;
        charger();
    }

    private void charger() {
        Map<String, Object> map = storage.getMap("scores_global");
        for (Map.Entry<String, Object> e : map.entrySet()) {
            scores.put(e.getKey(), SimpleJson.toInt(e.getValue(), 0));
        }
    }

    public synchronized void ajouterScore(String nom, int score) {
        scores.put(nom, scores.getOrDefault(nom, 0) + score);
    }

    public synchronized void sauvegarder() {
        Map<String, Object> map = new LinkedHashMap<>();
        scores.forEach((k, v) -> map.put(k, v));
        storage.sauvegarder("scores_global", map);
    }
}