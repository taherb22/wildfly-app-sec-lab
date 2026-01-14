package xyz.kaaniche.phoenix.iam.rest.dto;

public class MfaVerifyRequest {
    private String username;
    private String password;
    private String code;

    public MfaVerifyRequest() {}

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
