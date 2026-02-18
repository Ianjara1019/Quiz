package client.model;

public class AuthRequest {
    private final boolean register;
    private final String username;
    private final String password;

    public AuthRequest(boolean register, String username, String password) {
        this.register = register;
        this.username = username;
        this.password = password;
    }

    public boolean isRegister() {
        return register;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
