#!/bin/bash

# Script pour arrêter tous les serveurs

echo "→ Arrêt de tous les serveurs Java..."

# Chercher et tuer tous les processus Java liés au projet
pkill -f "serveur.ServeurCentralDistribue"
pkill -f "serveur.ServeurThemeDistribue"
pkill -f "client.ClientDistribue"

echo "✓ Tous les serveurs ont été arrêtés"