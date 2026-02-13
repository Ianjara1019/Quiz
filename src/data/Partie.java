package data;

import java.util.List;

public class Partie {
    private Joueur joueur;
    private List<Question> questions;
    private int index = 0;
    private int score = 0;

    public Partie(Joueur joueur, List<Question> questions) {
        this.joueur = joueur;
        this.questions = questions;
    }

    public Question getQuestionCourante() {
        if (index < questions.size())
            return questions.get(index);
        return null;
    }

    public void repondre(String rep) {
        if (questions.get(index).estCorrecte(rep)) {
            score += 10;
        }
        index++;
    }

    public int getScore() {
        return score;
    }

    public Joueur getJoueur() {
        return joueur;
    }
}