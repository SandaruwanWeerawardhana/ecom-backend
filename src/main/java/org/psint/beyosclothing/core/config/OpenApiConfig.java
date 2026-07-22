package org.psint.beyosclothing.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 * Provides interactive API documentation accessible at /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:Beyos Clothing API}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Beyos Clothing E-Commerce API")
                        .version("1.0.0")
                        .description("""
                                ## Beyos Clothing - Complete E-Commerce Backend API
                                
                                This API provides comprehensive endpoints for managing an e-commerce clothing platform including:
                                
                                ### Features:
                                - **Authentication & Authorization**: JWT-based authentication with role-based access control
                                - **Admin Management**: User, role, and permission management
                                - **Product Management**: Product catalog, categories, inventory
                                - **Customer Management**: Customer profiles and preferences
                                - **Order Management**: Order processing and tracking
                                - **Payment Integration**: Payment processing
                                - **File Management**: Image upload and management (AWS S3)
                                
                                ### Security:
                                - Most endpoints require authentication via JWT token
                                - Use the "Authorize" button to add your Bearer token
                                - Token format: `Bearer <your_jwt_token>`
                                
                                ### Getting Started:
                                1. Register or login to get JWT token
                                2. Click "Authorize" button and enter: `Bearer <token>`
                                3. Try out any API endpoint
                                """)
                        .contact(new Contact()
                                .name("Beyos Clothing Development Team")
                                .email("support@beyosclothing.com")
                                .url("https://beyosclothing.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://beyosclothing.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.beyosclothing.com")
                                .description("Production Server")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .name("Bearer Authentication")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token obtained from login endpoint")));
    }
}
