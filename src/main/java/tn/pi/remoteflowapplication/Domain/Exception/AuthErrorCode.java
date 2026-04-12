package tn.pi.remoteflowapplication.domain.exception;

public enum AuthErrorCode {
    AUTH_INVALID("auth.login.failed"),
    AUTH_TEMP_LOCK("auth.lock.temporary"),
    AUTH_ACCOUNT_DISABLED("auth.account.disabled"),
    PASSWORD_UPDATE_REQUIRED("auth.password.update_required");

    private final String normalizedName;

    AuthErrorCode(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }
}
