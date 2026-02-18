package serveur.view;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Vue console centralisée pour les serveurs.
 * Toute sortie console passe par cette classe pour un affichage cohérent.
 */
public class ConsoleLogger {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final String prefix;

    public ConsoleLogger(String prefix) {
        this.prefix = prefix;
    }

    // --- Bannières ---

    public void printBannerMaster(int portCoordination, int portClients) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   SERVEUR MAÎTRE DÉMARRÉ              ║");
        System.out.printf( "║   Port coordination: %-16d ║%n", portCoordination);
        System.out.printf( "║   Port clients:      %-16d ║%n", portClients);
        System.out.println("╚════════════════════════════════════════╝");
    }

    public void printBannerSlave(String id, String theme, int port, int partDebut, int partFin) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.printf( "║ SERVEUR ESCLAVE: %-20s ║%n", id);
        System.out.printf( "║ Thème: %-30s ║%n", theme);
        System.out.printf( "║ Port: %-31d ║%n", port);
        System.out.printf( "║ Partition scores: %-3d - %-14d ║%n", partDebut, partFin);
        System.out.println("╚════════════════════════════════════════╝");
    }

    // --- Classement ---

    public void printClassement(List<Map.Entry<String, Integer>> classement, int limit) {
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║        CLASSEMENT GLOBAL               ║");
        System.out.println("╠════════════════════════════════════════╣");
        int count = 0;
        for (Map.Entry<String, Integer> entry : classement) {
            if (limit > 0 && ++count > limit) break;
            System.out.printf("║ %-25s %10d pts ║%n", entry.getKey(), entry.getValue());
        }
        if (classement.isEmpty()) {
            System.out.println("║           (aucun score)                ║");
        }
        System.out.println("╚════════════════════════════════════════╝\n");
    }

    // --- Serveurs ---

    public void printEtatServeurs(List<String> lignes) {
        System.out.println("\n=== ÉTAT DES SERVEURS ===");
        for (String l : lignes) {
            System.out.println(l);
        }
        System.out.println("========================\n");
    }

    // --- Messages courants ---

    public void info(String message) {
        System.out.println(horodatage() + " [" + prefix + "] " + message);
    }

    public void success(String message) {
        System.out.println(horodatage() + " [" + prefix + "] ✓ " + message);
    }

    public void warn(String message) {
        System.out.println(horodatage() + " [" + prefix + "] ⚠ " + message);
    }

    public void error(String message) {
        System.err.println(horodatage() + " [" + prefix + "] ✗ " + message);
    }

    public void waiting(String message) {
        System.out.println(horodatage() + " [" + prefix + "] → " + message);
    }

    public void aggregation(String message) {
        System.out.println(horodatage() + " [" + prefix + "] ⟳ " + message);
    }

    private String horodatage() {
        return LocalDateTime.now().format(FMT);
    }
}
