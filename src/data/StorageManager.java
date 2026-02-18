package data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Gestionnaire centralisé du fichier storage.json.
 *
 * <p>Chaque lecture utilise un cache en mémoire.
 * Chaque écriture relit le fichier disque, fusionne la section modifiée,
 * puis écrit atomiquement (tmp + rename) pour minimiser les conflits
 * entre processus.</p>
 */
public class StorageManager {

    private final Path path;
    private Map<String, Object> cache;

    public StorageManager(String chemin) {
        this.path = Paths.get(chemin);
        this.cache = lireFichier();
        System.out.println("✓ StorageManager chargé depuis " + chemin);
    }

    // ─────────────── Lecture (depuis le cache) ───────────────

    @SuppressWarnings("unchecked")
    public synchronized List<Map<String, Object>> getList(String key) {
        Object v = cache.get(key);
        if (v instanceof List) {
            return new ArrayList<>((List<Map<String, Object>>) v);
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> getMap(String key) {
        Object v = cache.get(key);
        if (v instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) v);
        }
        return new LinkedHashMap<>();
    }

    // ─────────── Écriture (read-modify-write atomique) ───────────

    /**
     * Met à jour une section de premier niveau et sauvegarde.
     */
    public synchronized void sauvegarder(String section, Object value) {
        Map<String, Object> fresh = lireFichier();
        fresh.put(section, value);
        ecrireAtomic(fresh);
        this.cache = fresh;
    }

    /**
     * Met à jour une sous-clé à l'intérieur de "scores_partitions".
     */
    @SuppressWarnings("unchecked")
    public synchronized void sauvegarderPartition(String partitionKey, Object value) {
        Map<String, Object> fresh = lireFichier();
        Object pObj = fresh.get("scores_partitions");
        Map<String, Object> partitions = pObj instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) pObj)
                : new LinkedHashMap<>();
        partitions.put(partitionKey, value);
        fresh.put("scores_partitions", partitions);
        ecrireAtomic(fresh);
        this.cache = fresh;
    }

    /**
     * Recharge le cache depuis le disque.
     */
    public synchronized void recharger() {
        this.cache = lireFichier();
    }

    // ─────────────────── I/O internes ───────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> lireFichier() {
        if (!Files.exists(path)) return structureVide();
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            Object parsed = SimpleJson.parse(content);
            if (parsed instanceof Map) return (Map<String, Object>) parsed;
        } catch (Exception e) {
            System.err.println("Erreur lecture storage.json: " + e.getMessage());
        }
        return structureVide();
    }

    private void ecrireAtomic(Map<String, Object> data) {
        try {
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tmp, SimpleJson.stringify(data), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde storage.json: " + e.getMessage());
            try {
                Files.writeString(path, SimpleJson.stringify(data), StandardCharsets.UTF_8);
            } catch (IOException e2) {
                System.err.println("Erreur fallback: " + e2.getMessage());
            }
        }
    }

    private static Map<String, Object> structureVide() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", new ArrayList<>());
        m.put("themes_json", new ArrayList<>());
        m.put("themes_txt", new LinkedHashMap<>());
        m.put("registre", new ArrayList<>());
        m.put("scores_global", new LinkedHashMap<>());
        m.put("scores_partitions", new LinkedHashMap<>());
        m.put("matches", new ArrayList<>());
        return m;
    }
}
