package data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Themes {
    private Map<String, List<Question>> themes = new HashMap<>();

    public Themes(String fichier) {
        charger(fichier);
    }

    private void charger(String fichier) {
        if (fichier != null && fichier.endsWith(".json")) {
            chargerJson(fichier);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(fichier))) {
            String ligne;
            while ((ligne = br.readLine()) != null) {
                String[] p = ligne.split(";");
                if (p.length < 3) continue;
                themes.putIfAbsent(p[0], new ArrayList<>());
                themes.get(p[0]).add(new Question(p[1], p[2]));
            }
        } catch (IOException e) {
            System.out.println("Erreur chargement thèmes");
        }
    }

    private void chargerJson(String fichier) {
        try {
            String content = Files.readString(new File(fichier).toPath(), StandardCharsets.UTF_8);
            Pattern objPattern = Pattern.compile("\\{[^}]*\\}");
            Matcher m = objPattern.matcher(content);
            while (m.find()) {
                String obj = m.group();
                String theme = extraireChamp(obj, "theme");
                String question = extraireChamp(obj, "question");
                String answer = extraireChamp(obj, "answer");
                if (theme == null || question == null || answer == null) continue;
                themes.putIfAbsent(theme, new ArrayList<>());
                themes.get(theme).add(new Question(question, answer));
            }
        } catch (IOException e) {
            System.out.println("Erreur chargement thèmes JSON");
        }
    }

    private String extraireChamp(String obj, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(obj);
        if (!m.find()) return null;
        return unescape(m.group(1));
    }

    private String unescape(String v) {
        return v.replace("\\\\n", "\n")
                .replace("\\\\t", "\t")
                .replace("\\\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public List<Question> getQuestions(String theme) {
        return themes.getOrDefault(theme, new ArrayList<>());
    }
}
