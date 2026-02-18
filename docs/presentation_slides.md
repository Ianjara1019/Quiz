# Présentation — Quiz Distribué

---

## 1. Vue d'ensemble
- Application Quiz distribuée : clients → maître → esclaves
- Rôle du maître : coordination, redirection, agrégation des scores
- Rôle des esclaves : gérer un thème, exécuter les parties, stocker une partition des scores

---

## 2. Flux principal (client)
1. Le client se connecte au maître (`ServeurCentralDistribue`)
2. Le maître redirige vers un esclave approprié (`RegistreServeurs.selectionnerServeur`)
3. Le client s'authentifie et joue en `SOLO` ou `MULTI` sur l'esclave

---

## 3. Stockage — `storage.json`
- Géré par `StorageManager` (cache mémoire + lecture/écriture atomique)
- Format JSON simple implémenté par `SimpleJson`
- Sections principales : `users`, `themes_json`, `themes_txt`, `registre`, `scores_global`, `scores_partitions`, `matches`
- Écritures : tmp + rename pour atomicité, méthodes `synchronized` pour threads

---

## 4. Architecture Master / Slave
- Enregistrement des esclaves : `REGISTER:`
- Heartbeats périodiques : `HEARTBEAT:` → maître marque inactifs si timeout
- Partitionnement des scores : hachage du nom du joueur → plage `partitionDebut`..`partitionFin`
- Agrégation : maître envoie `GET_SCORES` puis fusionne (`ScoreService.fusionnerMax`)

---

## 5. Fichiers clés (où trouver le code)
- Maître : `src/serveur/ServeurCentralDistribue.java`
- Esclave : `src/serveur/ServeurThemeDistribue.java`
- Registre & sélection : `src/serveur/RegistreServeurs.java`
- Stockage JSON : `src/data/StorageManager.java`, `src/data/SimpleJson.java`
- Logique de jeu : `src/data/Question.java`, `src/serveur/service/MatchmakingService.java`, `src/serveur/MatchSolo.java`

---

## 6. Points à mentionner / Limitations
- Pas de réplication automatique des partitions d'esclave
- Sécurité basique par token partagé (optionnel)
- Tolérance d'orthographe configurable : `LEVENSHTEIN_THRESHOLD` dans `Question.java`

---

## 7. Démo rapide (commande)
1. Compiler : `javac -d out $(find src -name "*.java")`
2. Lancer maître : `java -cp out serveur.ServeurCentralDistribue`
3. Lancer esclave : `java -cp out serveur.ServeurThemeDistribue S1 Maths 5001 0 33`
4. Lancer client : `java -cp out client.ClientDistribue`

