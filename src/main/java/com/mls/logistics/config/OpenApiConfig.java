package com.mls.logistics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation configuration.
 *
 * Includes Bearer token authentication scheme so Swagger UI
 * can send JWT tokens when testing protected endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI molsOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("MOLS — Multimodal Operative Logistics System API")
                        .version("1.0.0")
                        .description("""
                                Backend REST API for managing logistics operations.
                                
                                Authentication: Use POST /api/auth/login to get a JWT token,
                                then click 'Authorize' and enter: Bearer <your-token>
                                
                                Roles:
                                - ADMIN: full access (GET, POST, PUT, PATCH, DELETE)
                                - OPERATOR: read access everywhere, plus write access
                                  (POST, PUT, PATCH, and DELETE on order items) on
                                  orders, order items and shipments — everything else
                                  is ADMIN-only
                                - AUDITOR: read only (GET)
                                """)
                        .contact(new Contact()
                                .name("MOLS Development")
                                .email("dev@mols.com"))
                        .license(new License()
                                .name("Private")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}