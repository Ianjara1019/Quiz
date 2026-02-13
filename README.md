# Quiz TCP Distribué

Un système de quiz multi-joueurs distribué en Java avec architecture maître-esclave, partitionnement des scores et équilibrage de charge.

## Table des Matières

1. [Présentation du Projet](#présentation-du-projet)
2. [Architecture](#architecture)
3. [Installation et Compilation](#installation-et-compilation)
4. [Lancement du Système](#lancement-du-système)
5. [Guide de Test](#guide-de-test)
6. [Fonctionnalités Détaillées](#fonctionnalités-détaillées)
7. [Configuration](#configuration)
8. [Dépannage](#dépannage)

---

## Présentation du Projet

### Objectif

Ce projet implémente un système de quiz distribué permettant à plusieurs joueurs de participer simultanément. Le système utilise une architecture TCP/IP avec:

- **1 serveur maître** : Gère les connexions, redirige les clients et agrège les scores
- **N serveurs esclaves** : Gèrent les quizzes par thème et stockent les scores
- **Clients** : Se connectent au serveur maître puis sont redirigés vers le serveur approprié

### Caractéristiques Principales

- Architecture maître-esclave avec communication TCP
- Partitionnement des scores par hachage du nom du joueur
- Équilibrage de charge entre serveurs du même thème
- Support multi-joueurs avec parties privées et mode tournoi
- Authentification utilisateurs avec hash sécurisé
- Historique des scores persistant (fichiers texte)

---

## Architecture

### Vue Globale

```
                    ┌───────────────────────────────────────┐
                    │         SERVEUR MAÎTRE                │
                    │     ServeurCentralDistribue           │
                    │                                       │
                    │  Port 6000: Réception clients         │
                    │  Port 6001: Coordination esclaves    │
                    │                                       │
                    │  Fonctions:                           │
                    │  • Enregistrement des esclaves       │
                    │  • Redirection des clients           │
                    │  • Agrégation des scores             │
                    │  • Équilibrage de charge             │
                    │  • Surveillance heartbeats           │
                    └───────────────┬───────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          │                         │                         │
┌─────────▼─────────┐     ┌─────────▼─────────┐     ┌─────────▼─────────┐
│   SERVEUR S1      │     │   SERVEUR S2      │     │   SERVEUR S3      │
│   (Esclave)       │     │   (Esclave)       │     │   (Esclave)       │
│                   │     │                   │     │                   │
│   Thème: Maths    │     │   Thème: Histoire │     │   Thème: Géographie│
│   Port: 5001      │     │   Port: 5002      │     │   Port: 5003       │
│   Partition: 0-33 │     │   Partition: 34-66│     │   Partition: 67-99│
│                   │     │                   │     │                   │
│   Gère:           │     │   Gère:           │     │   Gère:           │
│   • Quiz Maths    │     │   • Quiz Histoire │     │   • Quiz Géo      │
│   • Scores hash   │     │   • Scores hash   │     │   • Scores hash   │
│     0-33          │     │     34-66         │     │     67-99          │
└───────────────────┘     └───────────────────┘     └───────────────────┘
                              ▲         ▲                  ▲
                              │         │                  │
                              └─────────┴──────────────────┘
                                           │
                                  ┌────────▼────────┐
                                  │     CLIENTS     │
                                  │  ClientDistribue │
                                  └─────────────────┘
```

### Flux de Connexion Client

```
┌─────────┐                ┌──────────┐               ┌──────────┐
│ CLIENT  │                │  MAÎTRE  │               │ ESCLAVE  │
└────┬────┘                └────┬─────┘               └────┬─────┘
     │                          │                          │
     │ 1. Connexion             │                          │
    >│                          │
 ├─────────────────────────     │                          │                          │
     │ 2. THEME?                │                          │
     │<─────────────────────────┤                          │
     │                          │                          │
     │ 3. "Maths"               │                          │
     ├─────────────────────────>│                          │
     │                          │                          │
     │                          │ 4. Sélectionne S1        │
     │                          │    (charge minimale)     │
     │                          │                          │
     │ 5. REDIRECT:localhost:5001                          │
     │<─────────────────────────┤                          │
     │                          │                          │
     │ 6. Connexion directe     │                          │
     ├────────────────────────────────────────────────────>│
     │                          │                          │
     │ 7. QUESTIONS...          │                          │
     │<────────────────────────────────────────────────────┤
     │                          │                          │
     │ 8. FIN Score=80          │                          │
     │<────────────────────────────────────────────────────┤
```

### Partitionnement des Scores

Chaque joueur est assigné à un serveur selon le hash de son nom:

```
                    ┌─────────────────────────────┐
                    │  Nom du Joueur              │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌──────────────────────────┐
                    │  hashCode() % 100        │
                    │  Produit: 0-99           │
                    └──────────┬───────────────┘
                               │
               ┌──────────────┼──────────────┐
               │              │              │
       ┌───────▼──────┐ ┌────▼─────┐ ┌─────▼──────┐
       │  hash: 0-33  │ │hash:34-66│ │hash: 67-99 │
       │              │ │          │ │            │
       │  Serveur S1  │ │Serveur S2│ │ Serveur S3 │
       │              │ │          │ │            │
       │  Stockage:   │ │Stockage: │ │ Stockage:  │
       │  scores_     │ │scores_   │ │ scores_    │
       │  0-33.txt    │ │34-66.txt │ │ 67-99.txt  │
       └──────────────┘ └──────────┘ └────────────┘

Exemples:
  "Alice"   → hash=16  → Serveur S1
  "Bob"     → hash=65  → Serveur S2
  "Charlie" → hash=49  → Serveur S2
  "Diana"   → hash=10  → Serveur S1
  "George"  → hash=82  → Serveur S3
```

---

## Installation et Compilation

### Prérequis

- Java JDK 8 ou supérieur
- Un terminal (Linux/macOS/Windows avec WSL)

### Compilation

```bash
# Compiler le projet
./compile.sh
```

Ce script:
1. Crée le répertoire `bin/` s'il n'existe pas
2. Compile tous les fichiers Java sources
3. Vérifie le succès de la compilation

### Structure des Répertoires

```
/
├── src/                    # Code source Java
│   ├── client/           # Client TCP
│   ├── data/             # Classes de données
│   └── serveur/          # Serveurs (maître + esclaves)
├── bin/                   # Fichiers .class compilés
├── data/                  # Données persistantes
│   ├── themes.txt        # Questions (format txt)
│   ├── themes.json       # Questions (format JSON)
│   ├── users.txt        # Utilisateurs
│   ├── scores_*.txt     # Scores partitionnés
│   └── registre_serveurs.txt  # État des serveurs
└── *.sh                  # Scripts de gestion
```

---

## Lancement du Système

### Méthode Automatique (Recommandée)

```bash
./run_distributed.sh
```

Ce script lance:
- 1 serveur maître (port 6000)
- 3 serveurs esclaves (ports 5001, 5002, 5003)

### Méthode Manuelle

```bash
# Terminal 1: Serveur Maître
java -cp bin serveur.ServeurCentralDistribue

# Terminal 2: Serveur S1 (Maths, partition 0-33)
java -cp bin serveur.ServeurThemeDistribue S1 Maths 5001 0 33

# Terminal 3: Serveur S2 (Histoire, partition 34-66)
java -cp bin serveur.ServeurThemeDistribue S2 Histoire 5002 34 66

# Terminal 4: Serveur S3 (Géographie, partition 67-99)
java -cp bin serveur.ServeurThemeDistribue S3 Géographie 5003 67 99
```

### Lancer un Client

```bash
# Connexion locale
java -cp bin client.ClientDistribue

# Connexion à un serveur distant
java -cp bin client.ClientDistribue 172.16.6.58
```

### Arrêter le Système

```bash
./kill_distributed.sh
```

---

## Guide de Test

### Test 1: Vérification de Base

1. **Lancer le système**
   ```bash
   ./run_distributed.sh
   ```

2. **Vérifier les serveurs**
   - Chaque terminal de serveur affiche son état
   - Le serveur maître montre les serveurs enregistrés

3. **Lancer un client**
   ```bash
   java -cp bin client.ClientDistribue
   ```

4. **Jouer une partie**
   - Choisir un thème (Maths, Histoire, Géographie)
   - Entrer un nom de joueur
   - Répondre aux questions

### Test 2: Multiples Clients

```bash
# Dans plusieurs terminaux
java -cp bin client.ClientDistribue
```

**Vérifications:**
- Les clients sont répartis entre les serveurs
- Le serveur maître affiche la charge de chaque serveur
- Les scores sont correctement stockés

### Test 3: Partitionnement des Scores

1. Jouer avec plusieurs joueurs différents
2. Vérifier les partitions créées:

```bash
cat data/scores_partition_0-33.txt
cat data/scores_partition_34-66.txt
cat data/scores_partition_67-99.txt
```

**Attendu:** Les joueurs sont répartis dans différentes partitions selon leur hash

### Test 4: Classement Global

1. Dans le menu du client, choisir "Classement global"
2. Ou vérifier dans le terminal du serveur maître avec l'option [2]

### Test 5: Connexion Distante

1. **Sur le serveur:**
   ```bash
   # Trouver l'IP du serveur
   hostname -I
   # Ouvrir les ports (6000, 6001, 5001-5003)
   ```

2. **Sur le client distant:**
   ```bash
   java -cp bin client.ClientDistribue <IP_SERVEUR>
   ```

---

## Fonctionnalités Détaillées

### Mode Multi-Joueurs (Manches)

- Les joueurs s'authentifient puis choisissent "Jouer"
- Le serveur regroupe automatiquement 2 à 4 joueurs pour lancer une partie
- Chaque partie contient plusieurs questions et un classement final

### Parties Privées

1. Après avoir choisi "Jouer", répondre "o" à "Partie privée ?"
2. Entrer un code de partie (ex: `SALON123`)
3. Seuls les joueurs avec le même code sont regroupés

### Mode Tournoi

1. Après "Jouer", répondre "oui" à "Mode tournoi ?"
2. La partie est jouée en plusieurs manches
3. Les scores sont additionnés sur l'ensemble des manches

### Authentification

- **Register**: Créer un nouveau compte
- **Login**: Se connecter avec un compte existant
- Les mots de passe sont hashés avec salt dans `data/users.txt`

### Historique des Scores

- **Historique personnel**: Consulter ses propres parties
- **Classement global**: Afficher le top des joueurs
- Stockage dans `data/scores_matches.txt`

---

## Configuration

### Variables d'Environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `QUIZ_SERVER_HOST` | IP publique annoncée par les serveurs | Auto-détecté |
| `QUIZ_SHARED_SECRET` | Secret partagé pour auth entre serveurs | Non défini |
| `QUIZ_CLIENT_TOKEN` | Token optionnel exigé par le serveur maître | Non défini |
| `QUIZ_PARTITION_MAX` | Modulo de partitionnement des scores | 100 |
| `QUIZ_TOURNAMENT_ROUNDS` | Nombre de manches en mode tournoi | 3 |
| `QUIZ_ROUND_TIMER_MS` | Timer par manche (millisecondes) | 30000 |
| `QUIZ_THEMES_FILE` | Chemin du fichier thèmes | data/themes.json |

### Exemple de Configuration

```bash
# Activer la sécurité
export QUIZ_SHARED_SECRET="monSecretFort123"
export QUIZ_CLIENT_TOKEN="tokenClient456"

# Lancer le système
./run_distributed.sh

# Lancer le client
java -cp bin client.ClientDistribue
```

### Format des Fichiers de Données

**themes.txt:**
```
Theme;Question;Reponse
Maths;Combien font 2+2?;4
Histoire;Annee de la Revolution Francaise?;1789
```

**themes.json:**
```json
[
  {
    "theme": "Maths",
    "question": "Question ici",
    "answer": "Reponse",
    "difficulty": 2,
    "points": 10
  }
]
```

**registre_serveurs.txt:**
```
id;host;port;theme;charge;actif;partDebut;partFin
S1;localhost;5001;Maths;0;true;0;33
```

---

## Dépannage

### Problèmes Courants

| Problème | Solution |
|----------|----------|
| "Aucun serveur disponible" | Attendre 2-3 secondes que tous les serveurs s'enregistrent |
| Client redirigé vers localhost | Vérifier `QUIZ_SERVER_HOST` |
| "ERREUR: Auth" | Vérifier `QUIZ_SHARED_SECRET` et `QUIZ_CLIENT_TOKEN` |
| Ports non accessibles | Ouvrir les ports 6000, 6001, 5001-5003 sur le pare-feu |

### Commandes de Nettoyage

```bash
# Arrêter tous les serveurs
./kill_distributed.sh

# Supprimer les données (ATTENTION: perte des scores)
rm data/scores_*.txt
rm data/registre_serveurs.txt

# Recompiler
./compile.sh

# Relancer
./run_distributed.sh
```

### Vérification de l'État

Dans le terminal du serveur maître:
```
[1] État des serveurs    → Affiche tous les serveurs enregistrés
[2] Classement            → Affiche le top 10 des joueurs
[3] Quitter               → Arrête le serveur
```

---

## Documentation Complémentaire

- **Démarrage rapide**: `DEMARRAGE_RAPIDE.md`
- **Diagrammes d'architecture**: `DIAGRAMMES.md`
- **Code source**: Consulter les fichiers dans `src/`

---

## Auteur

Projet créé dans le cadre du cours de Systèmes Distribués (S3)
