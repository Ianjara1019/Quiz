package data;

import java.util.*;

public class Themes {
    private Map<String, List<Question>> themes = new HashMap<>();

    public Themes(StorageManager storage) {
        charger(storage);
    }

    @SuppressWarnings("unchecked")
    private void charger(StorageManager storage) {
        // Charger les thèmes JSON (format riche : difficulty + points)
        List<Map<String, Object>> jList = storage.getList("themes_json");
        for (Map<String, Object> q : jList) {
            String t     = SimpleJson.toStr(q.get("theme"), null);
            String quest = SimpleJson.toStr(q.get("question"), null);
            String ans   = SimpleJson.toStr(q.get("answer"), null);
            if (t == null || quest == null || ans == null) continue;
            int diff = SimpleJson.toInt(q.get("difficulty"), 1);
            int pts  = SimpleJson.toInt(q.get("points"), 10);
            themes.computeIfAbsent(t, k -> new ArrayList<>())
                  .add(new Question(quest, ans, diff, pts));
        }
        // Charger les thèmes TXT (format simple)
        Map<String, Object> tMap = storage.getMap("themes_txt");
        for (Map.Entry<String, Object> entry : tMap.entrySet()) {
            String t = entry.getKey();
            if (!(entry.getValue() instanceof List)) continue;
            List<Map<String, Object>> qs = (List<Map<String, Object>>) entry.getValue();
            for (Map<String, Object> q : qs) {
                String quest = SimpleJson.toStr(q.get("question"), null);
                String ans   = SimpleJson.toStr(q.get("answer"), null);
                if (quest != null && ans != null) {
                    themes.computeIfAbsent(t, k -> new ArrayList<>())
                          .add(new Question(quest, ans));
                }
            }
        }
    }

    public List<Question> getQuestions(String theme) {
        return themes.getOrDefault(theme, new ArrayList<>());
    }

    public List<String> getThemeNames() {
        List<String> noms = new ArrayList<>(themes.keySet());
        Collections.sort(noms);
        return noms;
    }
}
