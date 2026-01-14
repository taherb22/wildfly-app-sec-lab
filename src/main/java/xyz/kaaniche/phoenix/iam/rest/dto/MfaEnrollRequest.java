package xyz.kaaniche.phoenix.iam.rest.dto;

public class MfaEnrollRequest {
    private String username;
    private String password;

    public MfaEnrollRequest() {}

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
