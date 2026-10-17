package cn.sqa2.nacams.auth;

import cn.sqa2.nacams.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class Access {
    private final HttpServletRequest request;
    public Access(HttpServletRequest request) { this.request = request; }
    public CurrentUser optional() { return (CurrentUser)request.getAttribute("currentUser"); }
    public CurrentUser user() {
        var user = optional();
        if (user == null) throw new ApiException(401, "请先登录");
        return user;
    }
    public CurrentUser admin() {
        var user = user();
        if (!"admin".equals(user.role())) throw new ApiException(403, "需要系统管理员权限");
        return user;
    }
    public CurrentUser librarian() {
        var user = user();
        if (!user.isLibrarian()) throw new ApiException(403, "需要图书管理教师权限");
        return user;
    }
}
