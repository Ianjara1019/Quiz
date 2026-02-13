package data;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MatchHistory {
    private final File fichier;
    private final Object verrou = new Object();
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MatchHistory(String chemin) {
        this.fichier = new File(chemin);
    }

    public static class PlayerScore {
        public final String username;
        public final int score;
        public final int rank;
        public final int total;

        public PlayerScore(String username, int score, int rank, int total) {
            this.username = username;
            this.score = score;
            this.rank = rank;
            this.total = total;
        }
    }

    public void enregistrerMatch(String matchId, String theme, long timestampMs, List<PlayerScore> scores) {
        synchronized (verrou) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(fichier, true))) {
                for (PlayerScore ps : scores) {
                    pw.println(matchId + ";" + timestampMs + ";" + theme + ";" + ps.username + ";" + ps.score
                        + ";" + ps.rank + ";" + ps.total);
                }
            } catch (IOException e) {
                System.err.println("Erreur sauvegarde historique: " + e.getMessage());
            }
        }
    }

    public List<String> getHistoriquePourUser(String username, int limit) {
        List<String> lignes = new ArrayList<>();
        if (!fichier.exists()) return lignes;
        synchronized (verrou) {
            try (BufferedReader br = new BufferedReader(new FileReader(fichier))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(";");
                    if (parts.length < 7) continue;
                    if (!parts[3].equals(username)) continue;
                    String matchId = parts[0];
                    long ts = Long.parseLong(parts[1]);
                    String theme = parts[2];
                    String score = parts[4];
                    String rank = parts[5];
                    String total = parts[6];
                    String date = FORMAT.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()));
                    lignes.add(date + ";" + matchId + ";" + theme + ";" + score + ";" + rank + "/" + total);
                }
            } catch (IOException e) {
                System.err.println("Erreur lecture historique: " + e.getMessage());
            }
        }
        if (lignes.size() > limit) {
            return lignes.subList(lignes.size() - limit, lignes.size());
        }
        return lignes;
    }
}
