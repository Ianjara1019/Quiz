# Diagrammes d'Architecture du Système Distribué

## 1. Architecture Globale

```
                    ┌───────────────────────────────────────┐
                    │     SERVEUR MAÎTRE                    │
                    │  (ServeurCentralDistribue)            │
                    │                                       │
                    │  Port 6000: Clients                   │
                    │  Port 6001: Coordination esclaves     │
                    │                                       │
                    │  Fonctions:                           │
                    │  • Enregistrement des esclaves        │
                    │  • Redirection des clients            │
                    │  • Agrégation des scores              │
                    │  • Équilibrage de charge              │
                    └───────────────┬───────────────────────┘
                                    │
                    ┌───────────────┼───────────────────┐
                    │               │                   │
         ┌──────────▼──────┐ ┌─────▼──────┐ ┌─────────▼────────┐
         │  SERVEUR S1     │ │ SERVEUR S2 │ │   SERVEUR S3     │
         │  (Esclave)      │ │ (Esclave)  │ │   (Esclave)      │
         │                 │ │            │ │                  │
         │  Thème: Maths   │ │ Thème: H.  │ │  Thème: Géo      │
         │  Port: 5001     │ │ Port: 5002 │ │  Port: 5003      │
         │  Partition:0-33 │ │ Part.:34-66│ │  Partition:67-99 │
         │                 │ │            │ │                  │
         │  Gère:          │ │ Gère:      │ │  Gère:           │
         │  • Quiz         │ │ • Quiz     │ │  • Quiz          │
         │  • Scores hash  │ │ • Scores h.│ │  • Scores hash   │
         │    0-33         │ │   34-66    │ │    67-99         │
         └─────────────────┘ └────────────┘ └──────────────────┘
                 ▲                 ▲                  ▲
                 │                 │                  │
                 └─────────────────┴──────────────────┘
                                   │
                          ┌────────▼────────┐
                          │    CLIENTS      │
                          │ (ClientDistrib) │
                          └─────────────────┘
```

## 2. Flux de Connexion Client

```
┌─────────┐                ┌──────────┐               ┌──────────┐
│ CLIENT  │                │  MAÎTRE  │               │ ESCLAVE  │
└────┬────┘                └────┬─────┘               └────┬─────┘
     │                          │                          │
     │ 1. Connexion             │                          │
     ├─────────────────────────>│                          │
     │                          │                          │
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
     │ 6. Connexion directe                                │
     ├────────────────────────────────────────────────────>│
     │                          │                          │
     │ 7. Nom ?                                            │
     │<────────────────────────────────────────────────────┤
     │                          │                          │
     │ 8. "Alice"                                          │
     ├────────────────────────────────────────────────────>│
     │                          │                          │
     │ 9. QUESTION:...                                     │
     │<────────────────────────────────────────────────────┤
     │                          │                          │
     │ 10. Réponses...          │                          │
     ├────────────────────────────────────────────────────>│
     │                          │                          │
     │ 11. FIN Score=80                                    │
     │<────────────────────────────────────────────────────┤
     │                          │                          │
```

## 3. Partitionnement des Scores (Hash)

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
  "Ethan"   → hash=2   → Serveur S1
  "George"  → hash=82  → Serveur S3
```

## 4. Équilibrage de Charge (Redondance)

```
Scénario: 2 serveurs Maths disponibles

    S1 (Maths, charge=0)    S4 (Maths, charge=0)
         │                        │
         │                        │
    ┌────▼────────────────────────▼────┐
    │   Serveur Maître                 │
    │   selectionnerServeur("Maths")   │
    │   → retourne serveur avec        │
    │      charge MINIMALE             │
    └──────────────┬───────────────────┘
                   │
         ┌─────────┴─────────┐
         │                   │
    Client 1             Client 2
    demande Maths        demande Maths
         │                   │
         ▼                   ▼
    Dirigé vers S1      Dirigé vers S4
    (charge=0)          (charge=0)
         │                   │
         ▼                   ▼
    S1.charge → 1       S4.charge → 1

Résultat: Charge équilibrée automatiquement
```

## 5. Flux de Sauvegarde des Scores

```
┌─────────────────────────────────────────────────────┐
│  Client "Alice" (hash=16) termine sur Serveur S3    │
│  (S3 gère partition 67-99)                          │
└───────────────────────┬─────────────────────────────┘
                        │
                        ▼
            ┌───────────────────────┐
            │ S3 calcule hash(Alice)│
            │ hash = 16             │
            └───────────┬───────────┘
                        │
                        ▼
            ┌───────────────────────────┐
            │ 16 ∈ [67,99] ?           │
            │ NON! Ce n'est pas ma     │
            │ partition                │
            └───────────┬───────────────┘
                        │
                        ▼
            ┌────────────────────────────┐
            │ Envoi au Serveur Maître:   │
            │ SCORE:Alice;80;S3          │
            └───────────┬────────────────┘
                        │
                        ▼
            ┌────────────────────────────┐
            │ Maître trouve que hash=16  │
            │ appartient à S1 (0-33)     │
            └───────────┬────────────────┘
                        │
                        ▼
            ┌────────────────────────────┐
            │ Score transmis à S1 ou     │
            │ stocké dans scores_global  │
            └────────────────────────────┘
```

## 6. Agrégation Périodique des Scores

```
                ┌─────────────────────────┐
                │  Serveur Maître         │
                │  (Timer: toutes les 30s)│
                └───────────┬─────────────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ▼                  ▼                  ▼
    ┌────────┐         ┌────────┐        ┌────────┐
    │   S1   │         │   S2   │        │   S3   │
    │        │         │        │        │        │
    │ GET_   │         │ GET_   │        │ GET_   │
    │ SCORES │         │ SCORES │        │ SCORES │
    └───┬────┘         └───┬────┘        └───┬────┘
        │                  │                  │
        ▼                  ▼                  ▼
  Alice;80          Bob;70           George;90
  Diana;60          Charlie;75       
  Ethan;50          Hannah;65        
        │                  │                  │
        └──────────────────┴──────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Fusion dans Map       │
              │  scoresGlobaux:        │
              │  Alice → 80            │
              │  Bob → 70              │
              │  Charlie → 75          │
              │  Diana → 60            │
              │  Ethan → 50            │
              │  George → 90           │
              │  Hannah → 65           │
              └────────────┬───────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │ Sauvegarde dans        │
              │ scores_global.txt      │
              └────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │ Affichage Classement:  │
              │ 1. George    90        │
              │ 2. Alice     80        │
              │ 3. Charlie   75        │
              │ ...                    │
              └────────────────────────┘
```

## 7. Enregistrement d'un Serveur Esclave

```
┌──────────────────┐                    ┌──────────────────┐
│ Serveur Esclave  │                    │ Serveur Maître   │
│ (S1: Maths)      │                    │                  │
└────────┬─────────┘                    └────────┬─────────┘
         │                                       │
         │ 1. Démarrage                          │
         │                                       │
         │ 2. REGISTER:S1;localhost;5001;        │
         │    Maths;0;33                         │
         ├──────────────────────────────────────>│
         │                                       │
         │                                       │ 3. Enregistrement
         │                                       │    dans registre
         │                                       │    
         │ 4. OK:REGISTERED                      │
         │<──────────────────────────────────────┤
         │                                       │
         │ 5. Envoi HEARTBEAT                    │
         │    toutes les 10 secondes             │
         ├──────────────────────────────────────>│
         │                                       │
         │ 6. OK:ALIVE                           │
         │<──────────────────────────────────────┤
         │                                       │
         │ 7. Prêt à recevoir des clients        │
         │                                       │
```

## 8. Structure des Fichiers de Données

```
data/
│
├── themes.txt                    (Questions par thème)
│   Format: Theme;Question;Reponse
│   Exemple: Maths;Combien font 2+2?;4
│
├── registre_serveurs.txt         (État des serveurs)
│   Format: id;host;port;theme;charge;actif;partDebut;partFin
│   Exemple: S1;localhost;5001;Maths;2;true;0;33
│
├── scores_global.txt             (Agrégation globale)
│   Format: nom;score
│   Exemple: Alice;100
│
├── scores_partition_0-33.txt     (Partition S1)
│   Format: nom;score
│   Joueurs avec hash 0-33
│
├── scores_partition_34-66.txt    (Partition S2)
│   Format: nom;score
│   Joueurs avec hash 34-66
│
└── scores_partition_67-99.txt    (Partition S3)
    Format: nom;score
    Joueurs avec hash 67-99
```

## 9. Légende des Symboles

```
┌─────┐
│ Box │  = Composant / Processus
└─────┘

  │
  ▼      = Flux unidirectionnel

  ├─────>
  │      = Communication / Message
  ▼

  ┌──┬──┐
  │  │  │ = Choix / Branchement
  ▼  ▼  ▼
```