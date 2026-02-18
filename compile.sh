#!/bin/bash

# Script de compilation du projet Quiz Distribué (MVC)

echo "╔════════════════════════════════════════╗"
echo "║   COMPILATION DU PROJET QUIZ (MVC)     ║"
echo "╚════════════════════════════════════════╝"

# Créer les répertoires nécessaires
mkdir -p bin
mkdir -p data

echo ""
echo "→ Compilation des fichiers Java..."

# Compiler tous les packages (data, serveur MVC, client MVC)
javac -d bin -sourcepath src \
    src/data/*.java \
    src/serveur/model/*.java \
    src/serveur/service/*.java \
    src/serveur/view/*.java \
    src/serveur/*.java \
    src/client/model/*.java \
    src/client/view/*.java \
    src/client/controller/*.java \
    src/client/*.java

if [ $? -eq 0 ]; then
    NB_CLASSES=$(find bin -name "*.class" | wc -l)
    echo "✓ Compilation réussie! ($NB_CLASSES classes générées)"
    echo ""
    echo "Packages compilés:"
    echo "  • data/              — Modèles de données (Question, Themes, Scores, Auth...)"
    echo "  • client/model/      — Configuration client"
    echo "  • client/view/       — Interface console client"
    echo "  • client/controller/ — Logique client"
    echo "  • serveur/model/     — Configurations serveur (ServerConfig, SlaveConfig)"
    echo "  • serveur/service/   — Services (ScoreService, Matchmaking, ProtocolParser)"
    echo "  • serveur/view/      — Logs et affichage serveur"
    echo "  • serveur/           — Contrôleurs serveur (Maître, Esclave, Match)"
    echo ""
    echo "Prochaines étapes:"
    echo "  1. Lancez le système: ./run_distributed.sh"
    echo "  2. Ou manuellement:"
    echo "     - Serveur maître: java -cp bin serveur.ServeurCentralDistribue"
    echo "     - Serveur esclave: java -cp bin serveur.ServeurThemeDistribue S1 Maths 5001 0 33"
    echo "     - Client: java -cp bin client.ClientDistribue"
else
    echo "✗ Erreur de compilation"
    echo ""
    echo "Astuce: Vérifiez que la structure MVC est correcte:"
    find src -name "*.java" | sort
    exit 1
fi