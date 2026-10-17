package cn.sqa2.nacams.auth;

import cn.sqa2.nacams.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthService auth;
    private final ObjectMapper json;
    private final String origins;
    public WebConfig(AuthService auth, ObjectMapper json, @Value("${app.cors-origins}") String origins) {
        this.auth=auth; this.json=json; this.origins=origins;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                if ("OPTIONS".equals(req.getMethod())) return true;
                String header = req.getHeader("Authorization");
                if (header != null && !req.getRequestURI().equals("/api/v1/auth/login")) {
                    try {
                        if (!header.startsWith("Bearer ")) throw new ApiException(401,"认证格式错误");
                        req.setAttribute("currentUser", auth.authenticate(header.substring(7)));
                    } catch (ApiException e) {
                        res.setStatus(e.code);res.setContentType("application/json;charset=UTF-8");
                        json.writeValue(res.getWriter(),new Api<>(e.code,e.getMessage(),null));return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/v1/**");
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**").allowedOrigins(origins.split(","))
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS").allowedHeaders("Authorization","Content-Type");
    }
}
