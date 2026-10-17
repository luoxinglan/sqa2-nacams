package cn.sqa2.nacams.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean public OpenAPI api(){return new OpenAPI().info(new Info().title("SQA2网络攻防对抗技术研究所管理系统").version("1.0")
            .description("韦政治后端核心接口；字段示例及陈彬模块待接入状态见docs接口规范。"))
            .components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));}
}
