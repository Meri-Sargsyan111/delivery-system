package com.example.aiservice.service;

import com.example.aiservice.client.OllamaClient;
import com.example.aiservice.extraction.DeliveryDetailsExtractor;
import com.example.aiservice.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock private OllamaClient ollamaClient;

    private final DeliveryDetailsExtractor deliveryDetailsExtractor = new DeliveryDetailsExtractor();

    private final ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);

    @Test
    void chat_returnsOllamaReply() {
        AiChatServiceImpl service = new AiChatServiceImpl(ollamaClient, deliveryDetailsExtractor);
        when(ollamaClient.generate(anyString(), anyString()))
                .thenReturn("Estimated delivery cost is approximately 3500 AMD.");

        String reply = service.chat("I want to send a 5 kg package from Yerevan to Gyumri.");

        assertThat(reply).isEqualTo("Estimated delivery cost is approximately 3500 AMD.");
    }

    @Test
    void chat_buildsPromptWithKnownInformationAndOriginalMessage() {
        AiChatServiceImpl service = new AiChatServiceImpl(ollamaClient, deliveryDetailsExtractor);
        when(ollamaClient.generate(anyString(), anyString())).thenReturn("reply");

        String originalMessage = "I want to send a 5 kg package from Yerevan to Gyumri.";
        service.chat(originalMessage);

        verify(ollamaClient).generate(systemPromptCaptor.capture(), userPromptCaptor.capture());
        assertThat(systemPromptCaptor.getValue()).containsIgnoringCase("delivery");

        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("Pickup city: Yerevan");
        assertThat(prompt).contains("Destination city: Gyumri");
        assertThat(prompt).contains("Weight: 5 kg");
        assertThat(prompt).contains("Package type: Unknown");
        assertThat(prompt).contains(originalMessage);
        assertThat(prompt).contains("Never ask again for information that is already known.");
    }

    @Test
    void chat_withNoExtractableDetails_marksEverythingUnknown() {
        AiChatServiceImpl service = new AiChatServiceImpl(ollamaClient, deliveryDetailsExtractor);
        when(ollamaClient.generate(anyString(), anyString())).thenReturn("reply");

        service.chat("I want to send a package.");

        verify(ollamaClient).generate(systemPromptCaptor.capture(), userPromptCaptor.capture());
        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("Pickup city: Unknown");
        assertThat(prompt).contains("Destination city: Unknown");
        assertThat(prompt).contains("Weight: Unknown");
        assertThat(prompt).contains("Package type: Unknown");
    }
}
