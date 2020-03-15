package dev.astamur.trading.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.astamur.trading.model.Trade;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Slf4j
public class AppConfig {
    private final Config config;

    private String subscriptionToken;
    private String orderToken;
    private URI subscriptionUri;
    private URI orderUri;
    private List<Trade> trades = new ArrayList<>();

    public AppConfig() {
        this(ConfigFactory.load());
    }

    public AppConfig(Config config) {
        this.config = config;
        init();
        log.info("The following app config is loaded: {}", this);
    }

    public String getSubscriptionToken() {
        return subscriptionToken;
    }

    public URI getSubscriptionUri() {
        return subscriptionUri;
    }

    public String getOrderToken() {
        return orderToken;
    }

    public URI getOrderUri() {
        return orderUri;
    }

    public List<Trade> getTrades() {
        return trades;
    }

    private void init() {
        try {
            subscriptionUri = new URI(config.getString("app.subscription-uri"));
            orderUri = new URI(config.getString("app.order-uri"));
            subscriptionToken = config.getString("app.subscription-token");
            orderToken = config.getString("app.order-token");

            List<? extends Config> tradeConfigs = config.getConfigList("app.trades");
            for (Config trade : tradeConfigs) {
                trades.add(Trade.builder()
                        .productId(trade.getString("product-id"))
                        .amount(trade.getDouble("amount"))
                        .buyPrice(trade.getDouble("buy-price"))
                        .stopLossPrice(trade.getDouble("stop-loss-price"))
                        .takeProfitPrice(trade.getDouble("take-profit-price"))
                        .leverage(trade.getInt("leverage"))
                        .build());
            }
        } catch (Throwable t) {
            log.error("Configuration error", t);
            throw new IllegalStateException(t);
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AppConfig.class.getSimpleName() + "[", "]")
                .add("subscriptionToken='" + subscriptionToken + "'")
                .add("orderToken='" + orderToken + "'")
                .add("subscriptionUri=" + subscriptionUri)
                .add("orderUri=" + orderUri)
                .add("trades=" + trades)
                .toString();
    }
}