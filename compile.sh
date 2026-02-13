#!/bin/bash

# Script de compilation du projet

echo "╔════════════════════════════════════════╗"
echo "║   COMPILATION DU PROJET                ║"
echo "╚════════════════════════════════════════╝"

# Créer les répertoires nécessaires
mkdir -p bin
mkdir -p data

echo ""
echo "→ Compilation des fichiers Java..."

# Compiler tous les fichiers
javac -d bin -sourcepath src \
    src/data/*.java \
    src/serveur/*.java \
    src/client/*.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation réussie!"
    echo ""
    echo "Fichiers compilés dans le répertoire 'bin/'"
    echo ""
    echo "Prochaines étapes:"
    echo "  1. Lancez le système: ./run_distributed.sh"
    echo "  2. Ou manuellement:"
    echo "     - Serveur maître: java -cp bin serveur.ServeurCentralDistribue"
    echo "     - Serveur esclave: java -cp bin serveur.ServeurThemeDistribue S1 Maths 5001 0 33"
    echo "     - Client: java -cp bin client.ClientDistribue"
else
    echo "✗ Erreur de compilation"
    exit 1
fi