package cn.sqa2.nacams.auth;

public record CurrentUser(long id, String username, String role, String identityType,
                          String name, boolean isDirector, boolean isLibrarian, String tokenHash) {}
