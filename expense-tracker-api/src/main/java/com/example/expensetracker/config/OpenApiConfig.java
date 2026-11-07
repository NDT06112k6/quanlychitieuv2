package com.example.expensetracker.config;

import com.example.expensetracker.common.web.ApiDocumentation;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String TITLE = "Expense Tracker API";
    private static final String VERSION = "v1";
    private static final String DESCRIPTION = "Stateless personal expense tracking with owner-scoped data and JWT authentication.";
    private static final String HTTP_BEARER = "bearer";
    private static final String JWT_FORMAT = "JWT";

    @Bean
    OpenAPI expenseTrackerOpenApi() {
        Info info = new Info().title(TITLE).version(VERSION).description(DESCRIPTION);
        SecurityScheme scheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme(HTTP_BEARER)
                .bearerFormat(JWT_FORMAT);
        Components components = new Components().addSecuritySchemes(ApiDocumentation.BEARER_SCHEME, scheme);
        SecurityRequirement requirement = new SecurityRequirement().addList(ApiDocumentation.BEARER_SCHEME);
        OpenAPI api = new OpenAPI().info(info).components(components).addSecurityItem(requirement);
        return api;
    }
}
