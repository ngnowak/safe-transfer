package com.nn.safetransfer.common.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI safeTransferOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SafeTransfer API")
                        .version("v1")
                        .description("""
                                REST API for tenant-scoped wallet management, deposits, wallet-to-wallet transfers,
                                ledger-derived balances, idempotent transfer creation, and asynchronous audit processing.
                                """)
                        .contact(new Contact()
                                .name("SafeTransfer"))
                        .license(new License()
                                .name("Interview demo project")));
    }
}
