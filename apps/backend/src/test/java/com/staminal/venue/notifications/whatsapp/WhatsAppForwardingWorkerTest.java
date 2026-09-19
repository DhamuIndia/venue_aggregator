package com.staminal.venue.notifications.whatsapp;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import java.util.function.Consumer;

class WhatsAppForwardingWorkerTest {
    @SuppressWarnings("unchecked")
    private void run(int status, boolean networkFailure) throws Exception {
        var repository = mock(WhatsAppForwardingRepository.class);
        var transaction = mock(TransactionTemplate.class);
        var client = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(repository.claim()).thenReturn(List.of(new WhatsAppForwardingRepository.Pending("id", "{}", 2)));
        doAnswer(invocation -> { ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(mock(TransactionStatus.class)); return null; })
                .when(transaction).executeWithoutResult(any());
        if (networkFailure) when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("private details"));
        else when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var whatsapp = new WhatsAppCloudApiProperties();
        whatsapp.setAppSecret("secret");
        new WhatsAppForwardingWorker(new WhatsAppForwardingProperties(true, URI.create("https://example.com/webhook"), "sender"),
                whatsapp, repository, transaction, client).forward();
        if (status == 200 && !networkFailure) {
            verify(repository).delivered("id");
            verify(repository, never()).retry(anyString(), anyInt(), anyString());
        } else {
            verify(repository).retry("id", 2, networkFailure ? "TRANSPORT_OR_SIGNING_ERROR" : "HTTP_" + status);
            verify(repository, never()).delivered(anyString());
        }
    }
    @Test void acknowledgesSuccess() throws Exception { run(200, false); }
    @Test void retriesProviderFailure() throws Exception { run(503, false); }
    @Test void retriesNetworkFailureWithoutLoggingSensitiveDetails() throws Exception { run(0, true); }
}
