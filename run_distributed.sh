#!/bin/bash

# Script de déploiement du système distribué
# Lance le serveur maître et 3 serveurs esclaves avec partitionnement

echo "╔════════════════════════════════════════╗"
echo "║   DÉPLOIEMENT SYSTÈME DISTRIBUÉ        ║"
echo "╚════════════════════════════════════════╝"

# Créer le répertoire data s'il n'existe pas
mkdir -p data

# Compiler tous les fichiers Java
echo ""
echo "→ Compilation des sources..."
javac -d bin -sourcepath src src/serveur/*.java src/client/*.java src/data/*.java

if [ $? -ne 0 ]; then
    echo "✗ Erreur de compilation"
    exit 1
fi

echo "✓ Compilation réussie"
echo ""

# Fonction pour lancer un serveur dans une nouvelle fenêtre terminal
launch_terminal() {
    local title=$1
    local cmd=$2
    
    # Détection du système
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        osascript -e "tell app \"Terminal\" to do script \"cd $(pwd) && echo '$title' && $cmd\""
    elif command -v gnome-terminal &> /dev/null; then
        # Linux avec GNOME
        gnome-terminal --title="$title" -- bash -c "$cmd; exec bash"
    elif command -v xterm &> /dev/null; then
        # Fallback: xterm
        xterm -T "$title" -e "$cmd; bash" &
    else
        # Fallback: lancer en arrière-plan
        echo "→ Lancement: $title"
        bash -c "$cmd" &
        sleep 1
    fi
}

echo "→ Lancement du serveur maître..."
launch_terminal "Serveur Maître" "java -cp bin serveur.ServeurCentralDistribue"
sleep 2

echo "→ Lancement des serveurs esclaves..."

# Serveur 1: Maths, port 5001, partition 0-33
launch_terminal "Serveur S1 (Maths)" "java -cp bin serveur.ServeurThemeDistribue S1 Maths 5001 0 33"
sleep 1

# Serveur 2: Histoire, port 5002, partition 34-66
launch_terminal "Serveur S2 (Histoire)" "java -cp bin serveur.ServeurThemeDistribue S2 Histoire 5002 34 66"
sleep 1

# # Serveur 3: Géographie, port 5003, partition 67-99
# launch_terminal "Serveur S3 (Géographie)" "java -cp bin serveur.ServeurThemeDistribue S3 Géographie 5003 67 99"
# sleep 1

# # Serveur 4: Maths (redondance), port 5004, partition 0-33
# launch_terminal "Serveur S4 (Maths)" "java -cp bin serveur.ServeurThemeDistribue S4 Maths 5004 0 33"
# sleep 1

echo ""
echo "╔════════════════════════════════════════╗"
echo "║   SYSTÈME DÉMARRÉ                      ║"
echo "║                                        ║"
echo "║   Serveur Maître: localhost:6000       ║"
echo "║   S1 (Maths):     localhost:5001       ║"
echo "║   S2 (Histoire):  localhost:5002       ║"
echo "║   S3 (Géo):       localhost:5003       ║"
echo "║   S4 (Maths):     localhost:5004       ║"
echo "║                                        ║"
echo "║   Pour lancer un client:               ║"
echo "║   java -cp bin client.ClientDistribue  ║"
echo "╚════════════════════════════════════════╝"
echo ""
echo "→ Pour tester, lancez un client dans un nouveau terminal"
echo "→ Pour arrêter tous les serveurs: ./kill.sh"