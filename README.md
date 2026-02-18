# Quiz TCP Distribué

Un système de quiz multi-joueurs distribué en Java avec architecture **MVC**, maître-esclave, partitionnement des scores et équilibrage de charge.

## Table des Matières

1. [Présentation du Projet](#présentation-du-projet)
2. [Architecture MVC](#architecture-mvc)
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

- **Architecture MVC** sur le client et le serveur (Model / View / Controller + Service)
- Architecture maître-esclave avec communication TCP
- Partitionnement des scores par hachage du nom du joueur
- Équilibrage de charge entre serveurs du même thème
- **Mode Solo** : partie individuelle instantanée avec statistiques détaillées
- **Mode Multi-joueurs** : parties privées avec matchmaking automatique
- **Scoring intelligent** : points pondérés par difficulté + bonus de vitesse
- **Correspondance floue** : tolérance aux fautes de frappe (Levenshtein)
- Authentification utilisateurs avec hash SHA-256 + salt
- Historique des scores persistant (fichiers texte)
- Configuration flexible via variables d'environnement (Builder pattern)

---

## Architecture MVC

### Structure des Packages

```
src/
├── client/
│   ├── ClientDistribue.java         # Point d'entrée (instancie MVC)
│   ├── model/
│   │   ├── ClientConfig.java        # Configuration (host, port, token, timeout)
│   │   └── AuthRequest.java         # Données d'authentification
│   ├── view/
│   │   └── ConsoleView.java         # Toute l'interface console (menus, affichage)
│   └── controller/
│       └── ClientController.java    # Logique client (connexion, redirection, jeu)
│
├── data/                            # Modèles de données partagés
│   ├── Question.java                # Question avec difficulté, points, Levenshtein
│   ├── Themes.java                  # Chargement thèmes (JSON/TXT)
│   ├── AuthManager.java             # Authentification SHA-256 + salt
│   ├── MatchHistory.java            # Historique des matchs
│   ├── Scores.java                  # Wrapper de score
│   ├── Joueur.java                  # Modèle joueur
│   └── Partie.java                  # État d'une partie
│
└── serveur/
    ├── ServeurCentralDistribue.java  # Contrôleur serveur maître
    ├── ServeurThemeDistribue.java    # Contrôleur serveur esclave
    ├── Match.java                    # Logique de match multi-joueurs
    ├── MatchSolo.java                # Logique de partie solo
    ├── PlayerSession.java            # Session TCP d'un joueur
    ├── RegistreServeurs.java         # Registre + load balancing
    ├── model/
    │   ├── ServerConfig.java         # Config maître (Builder, env vars)
    │   └── SlaveConfig.java          # Config esclave (Builder, CLI + env vars)
    ├── service/
    │   ├── ScoreService.java         # Gestion scores thread-safe + persistance
    │   ├── MatchmakingService.java   # File d'attente + création de matchs
    │   └── ProtocolParser.java       # Validation/extraction du protocole TCP
    └── view/
        └── ConsoleLogger.java        # Logging centralisé avec timestamps
```

### Séparation MVC

| Couche | Client | Serveur |
|--------|--------|---------|
| **Model** | `ClientConfig`, `AuthRequest` | `ServerConfig`, `SlaveConfig` + tout `data/` |
| **View** | `ConsoleView` (menus, affichage) | `ConsoleLogger` (logs, bannières) |
| **Controller** | `ClientController` (flux réseau) | `ServeurCentral`, `ServeurTheme`, `Match` |
| **Service** | — | `ScoreService`, `MatchmakingService`, `ProtocolParser` |

### Vue Globale Réseau

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
├── src/                          # Code source Java (MVC)
│   ├── client/                  # Client TCP
│   │   ├── model/              # Config, données auth
│   │   ├── view/               # Interface console
│   │   └── controller/         # Logique de jeu
│   ├── data/                    # Modèles partagés
│   └── serveur/                 # Serveurs (maître + esclaves)
│       ├── model/              # Configurations (Builder pattern)
│       ├── service/            # Services métier
│       └── view/               # Logging serveur
├── bin/                          # Fichiers .class compilés
├── data/                         # Données persistantes
│   ├── themes.json              # Questions (format JSON avec difficulté/points)
│   ├── themes.txt               # Questions (format TXT basique)
│   ├── users.txt                # Utilisateurs (hash SHA-256 + salt)
│   ├── scores_*.txt             # Scores partitionnés
│   ├── scores_matches.txt       # Historique des matchs
│   └── registre_serveurs.txt    # État des serveurs
└── *.sh                          # Scripts de gestion
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

### Mode Solo

1. Dans le menu principal, choisir **"Jouer"**
2. Sélectionner **"1. Solo"** dans le choix de mode
3. La partie démarre **immédiatement** sans attendre d'autres joueurs
4. 10 questions sont posées une par une (15 secondes max par question)
5. Après chaque réponse, retour immédiat : correct/incorrect + points gagnés + combo
6. En fin de partie, un bilan complet est affiché :
   - Score total
   - Bonnes réponses / total
   - % de réussite
   - Temps moyen par question
   - Meilleure série (combo)
   - Mention (EXCELLENT ≥90%, BIEN ≥70%, PASSABLE ≥50%, À AMÉLIORER)

### Mode Multi-Joueurs

- Les joueurs s'authentifient puis choisissent **"2. Multi"** dans le choix de mode
- Le serveur regroupe automatiquement 2 à 4 joueurs pour lancer une partie
- Chaque partie contient plusieurs questions et un classement final

### Parties Privées (Mode Multi uniquement)

1. Après avoir choisi **"2. Multi"**, répondre "o" à "Partie privée ?"
2. Entrer un code de partie (ex: `SALON123`)
3. Seuls les joueurs avec le même code sont regroupés

### Scoring Intelligent

Les points sont calculés de manière dynamique :

- **Difficulté** : Chaque question a un niveau (★ facile, ★★ moyen, ★★★ difficile)
  - Facile : ×1 (10 pts de base)
  - Moyen : ×1.5 (15 pts)
  - Difficile : ×2 (20 pts)
- **Bonus de vitesse** : Jusqu'à +50% des points si le joueur répond rapidement
  - Plus la réponse est rapide, plus le bonus est élevé
- **Feedback immédiat** : Le client reçoit `CORRECT:EXACT` ou `CORRECT:FUZZY` avec les points gagnés

### Correspondance Floue (Levenshtein)

Les réponses sont tolérantes aux fautes :
- **Normalisation** : accents supprimés, casse ignorée, espaces normalisés
- **Distance de Levenshtein** : si la distance relative est ≤ 25%, la réponse est acceptée
- Exemple : `"Napoloen"` est accepté pour `"Napoléon"`

### Lister les Thèmes

Le client peut voir la liste des thèmes disponibles avant de jouer (menu option 4).

### Authentification

- **Register**: Créer un nouveau compte
- **Login**: Se connecter avec un compte existant
- Les mots de passe sont hashés avec SHA-256 + salt dans `data/users.txt`

### Historique des Scores

- **Historique personnel**: Consulter ses propres parties
- **Classement global**: Afficher le top des joueurs
- Stockage dans `data/scores_matches.txt`

---

## Configuration

### Variables d'Environnement — Serveur Maître (`ServerConfig`)

| Variable | Description | Défaut |
|----------|-------------|--------|
| `QUIZ_PORT_CLIENTS` | Port d'écoute clients | `6000` |
| `QUIZ_PORT_COORDINATION` | Port coordination esclaves | `6001` |
| `QUIZ_SERVER_HOST` | IP publique annoncée | Auto-détecté |
| `QUIZ_SHARED_SECRET` | Secret partagé entre serveurs | Non défini |
| `QUIZ_CLIENT_TOKEN` | Token optionnel exigé des clients | Non défini |
| `QUIZ_SOCKET_TIMEOUT_MS` | Timeout socket TCP (ms) | `120000` |
| `QUIZ_HEARTBEAT_TIMEOUT_MS` | Délai heartbeat avant déconnexion | `30000` |
| `QUIZ_HEARTBEAT_CHECK_MS` | Intervalle vérification heartbeat | `10000` |
| `QUIZ_AGGREGATION_INTERVAL_MS` | Intervalle agrégation scores | `15000` |
| `QUIZ_PARTITION_MAX` | Modulo partitionnement scores | `100` |
| `QUIZ_THEMES_FILE` | Chemin du fichier thèmes | `data/themes.json` |
| `QUIZ_SCORES_GLOBAL_FILE` | Fichier scores global | `data/scores_global.txt` |

### Variables d'Environnement — Serveur Esclave (`SlaveConfig`)

| Variable | Description | Défaut |
|----------|-------------|--------|
| `QUIZ_MASTER_HOST` | Hôte du serveur maître | `localhost` |
| `QUIZ_MASTER_COORD_PORT` | Port coordination du maître | `6001` |
| `QUIZ_MIN_PLAYERS` | Joueurs minimum pour lancer un match | `2` |
| `QUIZ_MAX_PLAYERS` | Joueurs maximum par match | `4` |
| `QUIZ_NB_QUESTIONS` | Questions par manche (multi) | `5` |
| `QUIZ_SOLO_NB_QUESTIONS` | Questions par partie solo | `10` |
| `QUIZ_ROUND_TIMER_MS` | Timer par manche (ms) | `45000` |
| `QUIZ_THEMES_FILE` | Chemin du fichier thèmes | `data/themes.json` |

### Variables d'Environnement — Client (`ClientConfig`)

| Variable | Description | Défaut |
|----------|-------------|--------|
| `QUIZ_SERVER_HOST` | Hôte du serveur maître | `localhost` |
| `QUIZ_CLIENT_TOKEN` | Token d'accès | Non défini |
| `QUIZ_SOCKET_TIMEOUT_MS` | Timeout socket (ms) | `120000` |

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

**themes.json:** (avec difficulté et points)
```json
[
  {
    "theme": "Maths",
    "question": "Combien font 2+2?",
    "answer": "4",
    "difficulty": 1,
    "points": 10
  },
  {
    "theme": "Histoire",
    "question": "Date de la Révolution Française?",
    "answer": "1789",
    "difficulty": 2,
    "points": 15
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
[2] Classement           → Affiche le top 10 des joueurs
[3] Statistiques         → Affiche les stats détaillées
[4] Quitter              → Arrête le serveur
```

---

## Documentation Complémentaire

- **Démarrage rapide**: `DEMARRAGE_RAPIDE.md`
- **Diagrammes d'architecture**: `DIAGRAMMES.md`
- **Code source**: Consulter les fichiers dans `src/`

---

## Auteur

Projet créé dans le cadre du cours de Systèmes Distribués (S3)
