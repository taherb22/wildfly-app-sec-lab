package xyz.kaaniche.phoenix.iam.store;

public class LoginSession {
    private String tenantName;
    private String requestedScopes;
    private String redirectUri;
    private String responseType;
    private String codeChallenge;
    private String state;
    private long createdAtEpoch;

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getRequestedScopes() {
        return requestedScopes;
    }

    public void setRequestedScopes(String requestedScopes) {
        this.requestedScopes = requestedScopes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }

    public void setCodeChallenge(String codeChallenge) {
        this.codeChallenge = codeChallenge;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getCreatedAtEpoch() {
        return createdAtEpoch;
    }

    public void setCreatedAtEpoch(long createdAtEpoch) {
        this.createdAtEpoch = createdAtEpoch;
    }
}
