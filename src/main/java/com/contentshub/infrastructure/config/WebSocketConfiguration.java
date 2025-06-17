package com.contentshub.infrastructure.config;

import com.contentshub.adapter.input.websocket.CollaborationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de WebSocket simplificada sin dependencias circulares
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfiguration {

    @Value("${app.websocket.endpoint:/ws}")
    private String websocketEndpoint;

    @Bean
    public HandlerMapping webSocketHandlerMapping(CollaborationWebSocketHandler collaborationHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put(websocketEndpoint + "/collaboration", collaborationHandler);

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setUrlMap(map);
        handlerMapping.setOrder(-1); // Prioridad alta

        return handlerMapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
