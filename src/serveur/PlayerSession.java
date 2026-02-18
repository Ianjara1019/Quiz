package serveur;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public class PlayerSession {
    private final String username;
    private final String roomCode;
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final CountDownLatch fin = new CountDownLatch(1);
    private volatile boolean closed = false;
    private int score = 0;

    public PlayerSession(String username, String roomCode, Socket socket, BufferedReader in, PrintWriter out) {
        this.username = username;
        this.roomCode = roomCode;
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    public String getUsername() {
        return username;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int delta) {
        score += delta;
    }

    public void send(String msg) {
        out.println(msg);
    }

    public String readLineWithTimeout(int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
        return in.readLine();
    }

    public void terminer() {
        fin.countDown();
    }

    public void attendreFinMatch() throws InterruptedException {
        fin.await();
    }

    public void closeQuiet() {
        if (closed) return;
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
    }

    public boolean isActive() {
        return !closed && !socket.isClosed();
    }
}
