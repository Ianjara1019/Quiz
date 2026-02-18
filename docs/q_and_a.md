# Questions fréquentes et réponses (préparation oral)

1. Q: Quel est le rôle du maître ?
   A: `ServeurCentralDistribue` coordonne les esclaves, redirige les clients, agrège périodiquement les scores et surveille les heartbeats.

2. Q: Que fait un esclave ?
   A: `ServeurThemeDistribue` gère les parties pour un thème, stocke l'historique et une partition des scores, et envoie des heartbeats au maître.

3. Q: Où sont stockées les données et comment ?
   A: Dans `storage.json`, géré par `StorageManager`. Il utilise un cache mémoire, lectures synchronisées et écritures atomiques (tmp + rename).

4. Q: Comment choisissez-vous quel esclave sert un client ?
   A: Le maître utilise `RegistreServeurs.selectionnerServeur(theme)` — filtre par thème puis choisit le serveur avec la charge la plus faible.

5. Q: Comment sont distribués les scores ?
   A: Par hachage du nom du joueur (modulo `partitionMax`), chaque esclave est responsable d'une plage de partitions.`selectionnerServeurScore` retrouve le responsable.

6. Q: Et si un esclave devient indisponible ?
   A: Le maître détecte l'absence de heartbeats et marque l'esclave inactif; les clients sont redirigés vers d'autres esclaves disponibles.

7. Q: Quels formats et bibliothèques JSON sont utilisés ?
   A: Implémentation maison `SimpleJson` (parser et writer minimal) — pas de dépendances externes.

8. Q: Comment la tolérance aux fautes de frappe est-elle gérée ?
   A: `Question.estCorrecte` normalise les chaînes puis calcule une distance de Levenshtein relative; le seuil est `LEVENSHTEIN_THRESHOLD`.

9. Q: Comment sécuriser les communications entre maître/esclaves ?
   A: Le système supporte un `secretPartage` optionnel et tokens clients; les messages REGISTER/HEARTBEAT/SCORE vérifient ce token.

10. Q: Quelles améliorations seraient pertinentes pour un usage en production ?
    A: Ajouter réplication des partitions (ou utiliser une base distribuée), chiffrement des communications (TLS), authentification forte, et un protocole de consensus pour la haute disponibilité.
