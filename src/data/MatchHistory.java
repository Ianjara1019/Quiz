package data;

import java.util.*;

public class MatchHistory {
    private final StorageManager storage;
    private final Object verrou = new Object();

    public MatchHistory(StorageManager storage) {
        this.storage = storage;
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
            List<Map<String, Object>> matches = storage.getList("matches");
            for (PlayerScore ps : scores) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("matchId", matchId);
                entry.put("timestampMs", timestampMs);
                entry.put("theme", theme);
                entry.put("username", ps.username);
                entry.put("score", ps.score);
                entry.put("rank", ps.rank);
                entry.put("total", ps.total);
                matches.add(entry);
            }
            storage.sauvegarder("matches", matches);
        }
    }

    public List<String> getHistoriquePourUser(String username, int limit) {
        List<String> lignes = new ArrayList<>();
        synchronized (verrou) {
            List<Map<String, Object>> matches = storage.getList("matches");
            for (Map<String, Object> m : matches) {
                String user = SimpleJson.toStr(m.get("username"), "");
                if (!user.equals(username)) continue;
                long ts      = SimpleJson.toLong(m.get("timestampMs"), 0);
                String mid   = SimpleJson.toStr(m.get("matchId"), "");
                String theme = SimpleJson.toStr(m.get("theme"), "");
                int score    = SimpleJson.toInt(m.get("score"), 0);
                int rank     = SimpleJson.toInt(m.get("rank"), 0);
                int total    = SimpleJson.toInt(m.get("total"), 0);
                java.time.Instant inst = java.time.Instant.ofEpochMilli(ts);
                String date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(inst.atZone(java.time.ZoneId.systemDefault()));
                lignes.add(date + ";" + mid + ";" + theme + ";" + score + ";" + rank + "/" + total);
            }
        }
        if (lignes.size() > limit) {
            return lignes.subList(lignes.size() - limit, lignes.size());
        }
        return lignes;
    }
}
