package com.nn.safetransfer.observability;

import com.nn.safetransfer.annotation.IntegrationTest;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.infrastructure.persistence.SpringDataWalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LogbookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SpringDataWalletRepository walletRepository;

    @AfterEach
    void cleanUp() {
        walletRepository.deleteAll();
    }

    @Test
    @WithMockUser
    void shouldLogApiRequestAndResponse(CapturedOutput output) throws Exception {
        // given
        var tenantId = UUID.randomUUID();
        var request = new CreateWalletRequest(UUID.randomUUID(), EUR.name());
        var path = "/api/v1/tenants/%s/wallets".formatted(tenantId);

        // when
        mockMvc.perform(post(path)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        assertThat(output)
                .contains("\"type\":\"request\"")
                .contains("\"type\":\"response\"")
                .contains(path)
                .contains(EUR.name());
    }
}
