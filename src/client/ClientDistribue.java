package client;

import client.controller.ClientController;
import client.model.ClientConfig;
import client.view.ConsoleView;

import java.util.Scanner;

/**
 * Client TCP qui se connecte au serveur maître
 * puis est redirigé vers le serveur esclave approprié
 */
public class ClientDistribue {
    public static void main(String[] args) {
        ClientConfig config = ClientConfig.fromEnvAndArgs(args);
        ConsoleView view = new ConsoleView(new Scanner(System.in));
        ClientController controller = new ClientController(config, view);
        controller.run();
    }
}
