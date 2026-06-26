package com.ashu.shelflife.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration. springdoc auto-discovers every {@code @RestController}
 * and its request/response models; this bean supplies the API metadata and registers a
 * JWT bearer security scheme so the Swagger UI "Authorize" button lets you paste an access
 * token (from {@code POST /auth/login}) and exercise the protected endpoints.
 *
 * <p>Swagger UI is served at {@code /swagger-ui.html} and the raw spec at {@code /v3/api-docs};
 * both are whitelisted in {@code SecurityConfig} so they are reachable without a token.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI shelfLifeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShelfLife API")
                        .description("""
                                Warehouse picking, inventory, and order-management backend.
                                Authenticate via POST /auth/login, then click "Authorize" and paste
                                the returned accessToken to call the protected endpoints. Roles:
                                CENTRAL_ADMIN (global) and HUB_PICKER (assigned-warehouse scope).""")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact().name("ShelfLife"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken from POST /auth/login")));
    }
}
