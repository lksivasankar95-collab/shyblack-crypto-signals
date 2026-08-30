package com.shyblack.cryptosignals.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI shyblackOpenApi() {
		final String bearer = "bearer-jwt";
		return new OpenAPI()
				.info(new Info()
						.title("ShyBlack Crypto Signals API")
						.version("0.0.1")
						.description("Portfolio tracker and signal platform backend."))
				.components(new Components().addSecuritySchemes(bearer, new SecurityScheme()
						.name(bearer)
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(bearer));
	}
}
