package serveur;

import data.SimpleJson;
import data.StorageManager;

import java.util.*;

/**
 * Registre des serveurs esclaves disponibles
 * Gère l'enregistrement, la charge et la sélection des serveurs
 */
public class RegistreServeurs {
    private Map<String, InfoServeur> serveurs = new HashMap<>();
    private final StorageManager storage;
    private final int partitionMax;

    public RegistreServeurs(StorageManager storage) {
        this.storage = storage;
        this.partitionMax = chargerPartitionMax();
        charger();
    }

    /**
     * Classe interne représentant les infos d'un serveur esclave
     */
    public static class InfoServeur {
        String id;
        String host;
        int port;
        String theme;
        int charge; // nombre de clients actuels
        boolean actif;
        int partitionDebut; // pour le partitionnement des scores
        int partitionFin;
        long dernierHeartbeat;
        long dernierChoix;

        public InfoServeur(String id, String host, int port, String theme, 
                          int partitionDebut, int partitionFin) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.theme = theme;
            this.charge = 0;
            this.actif = true;
            this.partitionDebut = partitionDebut;
            this.partitionFin = partitionFin;
            this.dernierHeartbeat = System.currentTimeMillis();
            this.dernierChoix = 0L;
        }

        @Override
        public String toString() {
            return id + ";" + host + ";" + port + ";" + theme + ";" + 
                   charge + ";" + actif + ";" + partitionDebut + ";" + partitionFin;
        }

        public static InfoServeur fromString(String ligne) {
            String[] parts = ligne.split(";");
            InfoServeur info = new InfoServeur(
                parts[0], parts[1], Integer.parseInt(parts[2]), 
                parts[3], Integer.parseInt(parts[6]), Integer.parseInt(parts[7])
            );
            info.charge = Integer.parseInt(parts[4]);
            info.actif = Boolean.parseBoolean(parts[5]);
            info.dernierHeartbeat = System.currentTimeMillis();
            info.dernierChoix = 0L;
            return info;
        }
    }

    /**
     * Enregistre un nouveau serveur esclave
     */
    public synchronized void enregistrer(InfoServeur serveur) {
        serveur.dernierHeartbeat = System.currentTimeMillis();
        serveurs.put(serveur.id, serveur);
        sauvegarder();
        System.out.println("✓ Serveur enregistré : " + serveur.id + 
                          " (theme=" + serveur.theme + ", partition=" + 
                          serveur.partitionDebut + "-" + serveur.partitionFin + ")");
    }

    /**
     * Sélectionne le meilleur serveur pour un thème donné
     * Algorithme : serveur du bon thème avec la charge la plus faible
     */
    public synchronized InfoServeur selectionnerServeur(String theme) {
        InfoServeur choisi = serveurs.values().stream()
            .filter(s -> s.actif && s.theme.equalsIgnoreCase(theme))
            .sorted(Comparator
                .comparingInt((InfoServeur s) -> s.charge)
                .thenComparingLong(s -> s.dernierChoix))
            .findFirst()
            .orElse(null);

        if (choisi != null) {
            choisi.dernierChoix = System.currentTimeMillis();
        }
        return choisi;
    }

    /**
     * Sélectionne le serveur responsable du stockage d'un score
     * Basé sur le hachage du nom du joueur (stockage distribué)
     */
    public synchronized InfoServeur selectionnerServeurScore(String nomJoueur) {
        int hash = Math.abs(nomJoueur.hashCode() % partitionMax); // partition 0-(max-1)
        
        return serveurs.values().stream()
            .filter(s -> s.actif && hash >= s.partitionDebut && hash <= s.partitionFin)
            .findFirst()
            .orElse(null);
    }

    /**
     * Récupère tous les serveurs actifs
     */
    public synchronized List<InfoServeur> getTousLesServeurs() {
        return new ArrayList<>(serveurs.values());
    }

    /**
     * Incrémente la charge d'un serveur
     */
    public synchronized void incrementerCharge(String serveurId) {
        InfoServeur serveur = serveurs.get(serveurId);
        if (serveur != null) {
            serveur.charge++;
            sauvegarder();
        }
    }

    /**
     * Décrémente la charge d'un serveur
     */
    public synchronized void decrementerCharge(String serveurId) {
        InfoServeur serveur = serveurs.get(serveurId);
        if (serveur != null) {
            serveur.charge = Math.max(0, serveur.charge - 1);
            sauvegarder();
        }
    }

    /**
     * Marque un serveur comme inactif
     */
    public synchronized void desactiverServeur(String serveurId) {
        InfoServeur serveur = serveurs.get(serveurId);
        if (serveur != null) {
            serveur.actif = false;
            sauvegarder();
        }
    }

    /**
     * Met à jour le heartbeat d'un serveur
     */
    public synchronized void mettreAJourHeartbeat(String serveurId) {
        InfoServeur serveur = serveurs.get(serveurId);
        if (serveur != null) {
            serveur.dernierHeartbeat = System.currentTimeMillis();
            if (!serveur.actif) {
                serveur.actif = true;
            }
        }
    }

    /**
     * Désactive les serveurs silencieux depuis trop longtemps
     */
    public synchronized void desactiverServeursSilencieux(long delaiMs) {
        long maintenant = System.currentTimeMillis();
        for (InfoServeur serveur : serveurs.values()) {
            if (serveur.actif && (maintenant - serveur.dernierHeartbeat) > delaiMs) {
                serveur.actif = false;
                System.out.println("⚠ Serveur inactif (timeout): " + serveur.id);
            }
        }
        sauvegarder();
    }

    /**
     * Charge le registre depuis le fichier
     */
    private void charger() {
        List<Map<String, Object>> list = storage.getList("registre");
        for (Map<String, Object> m : list) {
            String id    = SimpleJson.toStr(m.get("id"), "");
            String host  = SimpleJson.toStr(m.get("host"), "localhost");
            int port     = SimpleJson.toInt(m.get("port"), 0);
            String theme = SimpleJson.toStr(m.get("theme"), "");
            int pDebut   = SimpleJson.toInt(m.get("partitionDebut"), 0);
            int pFin     = SimpleJson.toInt(m.get("partitionFin"), 0);
            InfoServeur info = new InfoServeur(id, host, port, theme, pDebut, pFin);
            info.charge = SimpleJson.toInt(m.get("charge"), 0);
            info.actif  = SimpleJson.toBool(m.get("actif"), true);
            info.dernierHeartbeat = System.currentTimeMillis();
            serveurs.put(id, info);
        }
    }

    /**
     * Sauvegarde le registre dans le fichier
     */
    private void sauvegarder() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (InfoServeur s : serveurs.values()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", s.id);
            m.put("host", s.host);
            m.put("port", s.port);
            m.put("theme", s.theme);
            m.put("charge", s.charge);
            m.put("actif", s.actif);
            m.put("partitionDebut", s.partitionDebut);
            m.put("partitionFin", s.partitionFin);
            list.add(m);
        }
        storage.sauvegarder("registre", list);
    }

    /**
     * Affiche l'état de tous les serveurs
     */
    public void afficherEtat() {
        System.out.println("\n=== ÉTAT DES SERVEURS ===");
        serveurs.values().forEach(s -> {
            System.out.printf("%s [%s:%d] Theme=%s Charge=%d Partition=%d-%d Actif=%s%n",
                s.id, s.host, s.port, s.theme, s.charge, 
                s.partitionDebut, s.partitionFin, s.actif ? "✓" : "✗");
        });
        System.out.println("========================\n");
    }

    private int chargerPartitionMax() {
        String value = System.getenv("QUIZ_PARTITION_MAX");
        if (value == null || value.isBlank()) {
            return 100;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 100;
        } catch (NumberFormatException e) {
            return 100;
        }
    }
}
