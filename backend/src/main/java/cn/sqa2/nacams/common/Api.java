package cn.sqa2.nacams.common;

public record Api<T>(int code, String message, T data) {
    public static <T> Api<T> ok(T data) { return new Api<>(0, "success", data); }
}
