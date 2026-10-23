package com.benchmark.rps.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "server", matchIfMissing = true)
public class WebServerModeFilter {

  @EventListener
  public void onWebServerReady(WebServerInitializedEvent event) {
    int port = event.getWebServer().getPort();
    System.out.println("Spring Boot server running at http://localhost:" + port);
  }
}
