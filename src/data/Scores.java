package data;

import java.io.*;
import java.util.*;

public class Scores {
    private Map<String, Integer> scores = new HashMap<>();
    private String fichier;

    public Scores(String fichier) {
        this.fichier = fichier;
    }

    public synchronized void ajouterScore(String nom, int score) {
        scores.put(nom, scores.getOrDefault(nom, 0) + score);
    }

    public synchronized void sauvegarder() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fichier))) {
            scores.forEach((k, v) -> pw.println(k + ";" + v));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}