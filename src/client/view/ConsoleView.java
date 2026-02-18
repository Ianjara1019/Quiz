package client.view;

import client.model.AuthRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleView {
    private final Scanner scanner;

    public ConsoleView(Scanner scanner) {
        this.scanner = scanner;
    }

    public void printHeader() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║          CLIENT QUIZ TCP               ║");
        System.out.println("╚════════════════════════════════════════╝\n");
    }

    public int askMainMenuChoice() {
        while (true) {
            System.out.println("\n=== Menu principal ===");
            System.out.println("1. Jouer");
            System.out.println("2. Historique personnel");
            System.out.println("3. Classement global");
            System.out.println("4. Lister les thèmes");
            System.out.println("5. Quitter");
            System.out.print("Votre choix: ");
            String choix = readLine();
            if ("1".equals(choix) || "2".equals(choix) || "3".equals(choix)
                || "4".equals(choix) || "5".equals(choix)) {
                return Integer.parseInt(choix);
            }
            showError("Choix invalide. Réessayez.");
        }
    }

    public String askTheme() {
        System.out.print("\nChoisissez votre thème: ");
        return readNonEmptyLine();
    }

    public String askUsername() {
        System.out.print("Username: ");
        return readNonEmptyLine();
    }

    public AuthRequest askAuth() {
        String choix;
        while (true) {
            System.out.print("1=Login, 2=Register: ");
            choix = readLine();
            if ("1".equals(choix) || "2".equals(choix)) {
                break;
            }
            showError("Choix invalide.");
        }
        System.out.print("Username: ");
        String user = readNonEmptyLine();
        System.out.print("Mot de passe: ");
        String pass = readNonEmptyLine();
        return new AuthRequest("2".equals(choix), user, pass);
    }

    public String askRoomCode() {
        if (!askYesNo("Partie privée ? (o/n): ")) {
            return null;
        }
        System.out.print("Code de partie: ");
        String code = readLine();
        return code == null || code.isBlank() ? null : code.trim();
    }

    /**
     * Demande au joueur s'il veut jouer en Solo ou en Multi-joueurs.
     * @return "SOLO" ou "MULTI"
     */
    public String askSoloOrMulti() {
        while (true) {
            System.out.println("\n=== Mode de jeu ===");
            System.out.println("1. Solo  (jouer seul, score personnel)");
            System.out.println("2. Multi (jouer contre d'autres joueurs)");
            System.out.print("Votre choix: ");
            String c = readLine();
            if ("1".equals(c)) return "SOLO";
            if ("2".equals(c)) return "MULTI";
            showError("Choix invalide. Entrez 1 ou 2.");
        }
    }

    public void showSoloStart(String message) {
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║            PARTIE SOLO                 ║");
        System.out.println("╚════════════════════════════════════════╝");
        // Extraire les infos du message SOLO_START:...
        String info = message.startsWith("SOLO_START:") ? message.substring(11) : message;
        for (String part : info.split(";")) {
            if (part.startsWith("THEME=")) System.out.println("Thème    : " + part.substring(6));
            else if (part.startsWith("NB_QUESTIONS=")) System.out.println("Questions: " + part.substring(13));
        }
        System.out.println();
    }

    public void showSoloQuestion(String message) {
        // Format: SOLO_QUESTION:2/10;[★★ +20pts] texte
        String payload = message.startsWith("SOLO_QUESTION:") ? message.substring(14) : message;
        int sep = payload.indexOf(';');
        String progress = sep >= 0 ? payload.substring(0, sep) : "?";
        String qtext    = sep >= 0 ? payload.substring(sep + 1) : payload;
        System.out.println("\n[" + progress + "] " + qtext);
    }

    public void showSoloResult(String message) {
        // Format: SOLO_CORRECT:EXACT;PTS=22;ELAPSED=3210ms;COMBO=3
        //      ou SOLO_WRONG:ANSWER=Paris
        if (message.startsWith("SOLO_CORRECT:")) {
            String rest = message.substring(13);
            String type = rest.startsWith("EXACT") ? "✓ Correct (exact)" : "✓ Correct (approximatif)";
            String pts = "", elapsed = "", combo = "";
            for (String p : rest.split(";")) {
                if (p.startsWith("PTS="))    pts    = p.substring(4);
                if (p.startsWith("ELAPSED=")) elapsed = p.substring(8);
                if (p.startsWith("COMBO="))  combo  = p.substring(6);
            }
            System.out.println(type + " | +" + pts + " pts | Temps : " + elapsed
                + (Integer.parseInt(combo) > 1 ? " | Combo x" + combo + " 🔥" : ""));
        } else if (message.startsWith("SOLO_WRONG:")) {
            String ans = message.substring(11).replace("ANSWER=", "");
            System.out.println("✗ Incorrect | Réponse : " + ans);
        }
    }

    public void showSoloEnd(String message) {
        // Format: SOLO_END:Score=120;Bonnes=8/10;Pct=80;TempsMoyen=4200ms;MeilleureCombo=4;Mention=BIEN
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║          FIN DE PARTIE SOLO            ║");
        System.out.println("╚════════════════════════════════════════╝");
        String payload = message.startsWith("SOLO_END:") ? message.substring(9) : message;
        for (String p : payload.split(";")) {
            if (p.startsWith("Score="))        System.out.println("  Score total    : " + p.substring(6) + " pts");
            else if (p.startsWith("Bonnes="))  System.out.println("  Bonnes réponses: " + p.substring(7));
            else if (p.startsWith("Pct="))     System.out.println("  Réussite       : " + p.substring(4) + "%");
            else if (p.startsWith("TempsMoyen=")) System.out.println("  Temps moyen    : " + p.substring(11));
            else if (p.startsWith("MeilleureCombo=")) System.out.println("  Meilleure série: " + p.substring(15));
            else if (p.startsWith("Mention=")) System.out.println("  Mention        : " + p.substring(8));
        }
    }

    public void showHistory(List<String> lignes, String titre) {
        System.out.println("\n=== " + titre + " ===");
        if (lignes.isEmpty()) {
            System.out.println("(Aucun résultat)");
            return;
        }
        for (String l : lignes) {
            System.out.println(l);
        }
    }

    public void showLeaderboard(List<String> lignes) {
        showHistory(lignes, "Classement global");
    }

    public void showThemes(List<String> themes) {
        System.out.println("\n=== Thèmes disponibles ===");
        if (themes.isEmpty()) {
            System.out.println("(Aucun thème trouvé)");
            return;
        }
        for (String t : themes) {
            System.out.println("- " + t);
        }
    }

    public void showInfo(String message) {
        System.out.println(message);
    }

    public void showError(String message) {
        System.out.println("✗ " + message);
    }

    public void showMatchStart(String message) {
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║         MATCH MULTI-JOUEURS            ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println(message);
    }

    public void showQuestion(int questionNum, String question) {
        System.out.println("Question " + questionNum + ": " + question);
    }

    public String askAnswer() {
        System.out.print("Votre réponse: ");
        return readLine();
    }

    public boolean askReplay() {
        return askYesNo("\nVoulez-vous rejouer ? (o/n): ");
    }

    public boolean askPlayAfterInfo() {
        return askYesNo("\nVoulez-vous choisir un thème et jouer maintenant ? (o/n): ");
    }

    private boolean askYesNo(String message) {
        System.out.print(message);
        String reponse = readLine();
        return reponse.equalsIgnoreCase("o") || reponse.equalsIgnoreCase("oui")
            || reponse.equalsIgnoreCase("y") || reponse.equalsIgnoreCase("yes");
    }

    private String readLine() {
        return scanner.nextLine();
    }

    private String readNonEmptyLine() {
        while (true) {
            String line = readLine();
            if (line != null && !line.isBlank()) {
                return line.trim();
            }
            showError("Valeur vide. Réessayez.");
        }
    }

    public List<String> readBlock(java.io.BufferedReader in, String endMarker) throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null && !endMarker.equals(line)) {
            lines.add(line);
        }
        return lines;
    }
}
