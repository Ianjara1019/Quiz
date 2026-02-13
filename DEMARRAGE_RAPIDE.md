# Guide de Démarrage Rapide

## 🚀 Démarrage en 3 Étapes

### Étape 1: Compilation
```bash
./compile.sh
```

### Étape 2: Démarrage du Système
```bash
./run_distributed.sh
```

Cela lance automatiquement:
- 1 serveur maître
- 4 serveurs esclaves (3 thèmes + 1 redondance)

### Étape 3: Lancer un Client
Dans un nouveau terminal:
```bash
java -cp bin client.ClientDistribue
```

**C'est tout!** 🎉

---

## 📋 Commandes Essentielles

### Compiler le projet
```bash
./compile.sh
```

### Lancer le système complet
```bash
./run_distributed.sh
```

### Lancer un client
```bash
java -cp bin client.ClientDistribue
```

### Tester avec plusieurs clients simultanés
```bash
./test_charge.sh
```

### Arrêter tous les serveurs
```bash
./kill_distributed.sh
```

---

## 🎮 Première Session de Jeu

1. **Lancez le système**
   ```bash
   ./run_distributed.sh
   ```

2. **Dans un nouveau terminal, lancez un client**
   ```bash
   java -cp bin client.ClientDistribue
   ```

3. **Suivez les instructions**
   - Choisissez un thème (Maths, Histoire, Géographie)
   - Entrez votre nom
   - Répondez aux questions

4. **Vérifiez le classement**
   - Dans le terminal du serveur maître
   - Appuyez sur `[2]` pour voir le classement

---

## 📊 Vérifier l'État du Système

### Dans le terminal du Serveur Maître

**Menu interactif:**
```
[1] État des serveurs
[2] Classement
[3] Quitter
```

**[1] État des serveurs** montre:
- Tous les serveurs enregistrés
- Leur charge actuelle
- Leur partition de scores
- Leur statut (actif/inactif)

**[2] Classement** montre:
- Top 10 des joueurs
- Leurs scores totaux
- Mise à jour toutes les 30 secondes

---

## 🧪 Tester le Système

### Test 1: Client Simple
```bash
java -cp bin client.ClientDistribue
```
Choisissez un thème et jouez

### Test 2: Plusieurs Clients Simultanés
```bash
./test_charge.sh
```
Lance 5 clients automatiques

Vérifiez dans le serveur maître que:
- La charge est bien répartie
- Les serveurs Maths ont plusieurs clients
- Les autres thèmes ont aussi des clients

### Test 3: Vérifier le Stockage Distribué

Après avoir joué avec plusieurs noms différents:

```bash
# Voir les partitions de scores créées
ls -la data/scores_partition_*.txt

# Contenu d'une partition
cat data/scores_partition_0-33.txt

# Scores globaux agrégés
cat data/scores_global.txt
```

Vous devriez voir:
- Différents joueurs dans différentes partitions
- Les scores agrégés dans le fichier global

---

## 🔧 Démarrage Manuel (Alternative)

Si vous préférez tout lancer manuellement:

### Terminal 1: Serveur Maître
```bash
java -cp bin serveur.ServeurCentralDistribue
```

### Terminal 2: Serveur S1 (Maths)
```bash
java -cp bin serveur.ServeurThemeDistribue S1 Maths 5001 0 33
```

### Terminal 3: Serveur S2 (Histoire)
```bash
java -cp bin serveur.ServeurThemeDistribue S2 Histoire 5002 34 66
```

### Terminal 4: Serveur S3 (Géographie)
```bash
java -cp bin serveur.ServeurThemeDistribue S3 Géographie 5003 67 99
```

### Terminal 5+: Clients
```bash
java -cp bin client.ClientDistribue
```

---

## 🎯 Cas d'Usage Typiques

### Cas 1: Démonstration du Prof
```bash
# Terminal 1
./run_distributed.sh

# Attendre 2-3 secondes que tout démarre

# Terminal 2
./test_charge.sh

# Montrer dans le Terminal 1 (serveur maître):
# [1] pour voir la charge répartie
# [2] pour voir le classement
```

### Cas 2: Test Manuel
```bash
# Lancer le système
./run_distributed.sh

# Ouvrir 3-4 terminaux et dans chacun:
java -cp bin client.ClientDistribue

# Choisir différents thèmes
# Observer la répartition dans le serveur maître
```

### Cas 3: Vérifier le Stockage Distribué
```bash
# Jouer avec des noms commençant par différentes lettres
# Exemple: Alice, Bob, Charlie, Diana, Ethan

# Vérifier les partitions:
cat data/scores_partition_0-33.txt
cat data/scores_partition_34-66.txt
cat data/scores_partition_67-99.txt

# Vous verrez que les noms sont répartis différemment
```

---

## ❓ FAQ Rapide

**Q: Le système ne démarre pas?**
```bash
# Vérifier la compilation
./compile.sh

# Vérifier qu'aucun serveur ne tourne déjà
./kill_distributed.sh

# Relancer
./run_distributed.sh
```

**Q: "Aucun serveur disponible pour X"?**
- Attendez 2-3 secondes que tous les serveurs s'enregistrent
- Vérifiez que les serveurs esclaves sont bien démarrés

**Q: Comment ajouter un nouveau thème?**
1. Modifiez `data/themes.txt`
2. Ajoutez vos questions au format: `Theme;Question;Reponse`
3. Lancez un serveur pour ce thème:
   ```bash
   java -cp bin serveur.ServeurThemeDistribue S5 MonTheme 5005 0 33
   ```

**Q: Comment voir les logs détaillés?**
- Tous les serveurs affichent leurs logs dans leur terminal respectif
- Le serveur maître montre toutes les opérations importantes

---

## 📚 Documentation Complète

Pour plus de détails:
- **Architecture**: `README_DISTRIBUE.md`
- **Technique**: `DOCUMENTATION_TECHNIQUE.md`
- **Diagrammes**: `DIAGRAMMES.md`

---

## 🎓 Points Clés pour le Prof

✅ **Stockage Distribué**: Hash des noms pour répartir les scores
✅ **Équilibrage de Charge**: Sélection du serveur le moins chargé
✅ **Évite Duplication**: Chaque score stocké UNE fois
✅ **Scalable**: Ajout facile de nouveaux serveurs
✅ **Fichiers Texte**: Pas de base de données
✅ **Ligne de Commande**: Pas d'interface web

---

## 🚨 En Cas de Problème

```bash
# Tuer tous les processus
./kill_distributed.sh

# Nettoyer les fichiers de données (ATTENTION: perte des scores)
rm data/scores_*.txt
rm data/registre_serveurs.txt

# Recompiler
./compile.sh

# Relancer
./run_distributed.sh
```

---

**Bon jeu!** 🎮