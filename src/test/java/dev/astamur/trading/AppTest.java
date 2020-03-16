package dev.astamur.trading;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.astamur.trading.config.AppConfig;
import dev.astamur.trading.model.Trade;
import org.asynchttpclient.AsyncHttpClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
public class AppTest {
    @Container
    public GenericContainer buxServer = new GenericContainer(
            new ImageFromDockerfile()
                    .withFileFromPath("bux-server.jar", Paths.get("docker/bux-server/bux-server.jar"))
                    .withFileFromPath("Dockerfile", Paths.get("docker/bux-server/Dockerfile")))
            .withExposedPorts(8080);

    /**
     * Dummy working application test :)
     */
    @Test
    public void shouldWorkForSomeTime() {
        Config cfg = ConfigFactory.load("application_test.conf");
        AppConfig config = mock(AppConfig.class);
        AsyncHttpClient httpClient = asyncHttpClient();

        when(config.getSubscriptionUri()).thenReturn(URI.create("http://localhost:" + buxServer.getMappedPort(8080)));
        when(config.getOrderUri()).thenReturn(URI.create("http://localhost:" + buxServer.getMappedPort(8080)));
        when(config.getSubscriptionToken()).thenReturn(cfg.getString("app.subscription-token"));
        when(config.getOrderToken()).thenReturn(cfg.getString("app.order-token"));
        when(config.getTrades()).thenReturn(List.of(Trade.builder()
                .productId("sb26493")
                .amount(10_000)
                .stopLossPrice(100_000) // Sell right after buy
                .buyPrice(100_000) // Buy right after start
                .takeProfitPrice(100_000) // Sell right after buy
                .leverage(1)
                .build()));
        App app = new App();
        new Thread(() -> app.start(config, httpClient)).start();

        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            fail("A problem occurred!");
        }

        app.close();
    }
}