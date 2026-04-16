package com.example.demo.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;

class ImpostorHintServiceTests {

    @Test
    void returnsFallbackWhenDisabled() {
        ImpostorHintService service = new ImpostorHintService(
                false,
                "https://api.datamuse.com",
                1200,
                3,
                mock(HttpClient.class));

        assertThat(service.generateHints("perro")).containsExactly("no tienes pistas :(");
    }

    @Test
    void returnsThreeNormalizedHintsWhenApiProvidesEnoughWords() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                "[{\"word\":\"perro\"},{\"word\":\" canino \"},{\"word\":\"mascota\"},{\"word\":\"canino\"},{\"word\":\"ladrido\"}]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        ImpostorHintService service = new ImpostorHintService(
                true,
                "https://api.datamuse.com",
                1200,
                3,
                httpClient);

        List<String> hints = service.generateHints("perro");

        assertThat(hints).containsExactly("canino", "mascota", "ladrido");
    }

    @Test
    void returnsFallbackWhenApiProvidesLessThanThreeValidWords() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[{\"word\":\"perro\"},{\"word\":\"canino\"}]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        ImpostorHintService service = new ImpostorHintService(
                true,
                "https://api.datamuse.com",
                1200,
                3,
                httpClient);

        assertThat(service.generateHints("perro")).containsExactly("no tienes pistas :(");
    }

    @Test
    void completesHintsUsingMlWhenRelTrgIsEmpty() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> relTrgResponse = (HttpResponse<String>) mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mlResponse = (HttpResponse<String>) mock(HttpResponse.class);

        when(relTrgResponse.statusCode()).thenReturn(200);
        when(relTrgResponse.body()).thenReturn("[]");

        when(mlResponse.statusCode()).thenReturn(200);
        when(mlResponse.body()).thenReturn(
                "[{\"word\":\"canino\"},{\"word\":\"kennel\"},{\"word\":\"labrador\"}]");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(relTrgResponse)
                .thenReturn(mlResponse);

        ImpostorHintService service = new ImpostorHintService(
                true,
                "https://api.datamuse.com",
                1200,
                3,
                httpClient);

        assertThat(service.generateHints("perro")).containsExactly("canino", "kennel", "labrador");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void returnsFallbackWhenApiCallFails() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("network error"));

        ImpostorHintService service = new ImpostorHintService(
                true,
                "https://api.datamuse.com",
                1200,
                3,
                httpClient);

        assertThat(service.generateHints("perro")).containsExactly("no tienes pistas :(");
    }
}
