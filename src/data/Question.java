package data;

public class Question {
    private String texte;
    private String reponse;

    public Question(String texte, String reponse) {
        this.texte = texte;
        this.reponse = reponse;
    }

    public String getTexte() {
        return texte;
    }

    public boolean estCorrecte(String rep) {
        return reponse.equalsIgnoreCase(rep.trim());
    }
}