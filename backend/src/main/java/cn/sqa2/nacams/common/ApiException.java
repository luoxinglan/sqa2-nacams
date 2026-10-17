package cn.sqa2.nacams.common;

public class ApiException extends RuntimeException {
    public final int code;
    public final Object data;
    public ApiException(int code, String message) { this(code, message, null); }
    public ApiException(int code, String message, Object data) {
        super(message); this.code = code; this.data = data;
    }
}
