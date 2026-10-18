package cn.sqa2.nacams.common;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 接口规范 §1：时间一律输出 "YYYY-MM-DD HH:mm"（24 小时制到分钟），纯日期输出 "YYYY-MM-DD"。 */
@Configuration
public class JacksonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer interfaceTimeFormats() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTime));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(date));
            builder.modulesToInstall(module);
        };
    }
}