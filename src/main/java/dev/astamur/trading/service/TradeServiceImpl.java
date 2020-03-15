package dev.astamur.trading.service;

import dev.astamur.trading.config.AppConfig;
import dev.astamur.trading.model.OrderResponse;
import dev.astamur.trading.model.Quote;
import dev.astamur.trading.model.Trade;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static dev.astamur.trading.config.HttpUtils.*;


@Slf4j
public class TradeServiceImpl implements TradeService {
    private static final int NEW = 0;           // No active order (can buy in that state)
    private static final int BUY_SENT = 1;      // Buy request was sent (no actions in that state)
    private static final int BOUGHT = 2;        // Order was bought (only sell is allowed after that state)
    private static final int SELL_SENT = 3;     // Sell request was sent (no actions in that state)
    private static final int SOLD = 4;          // Order was sold (terminal state)


    private final AppConfig config;
    private final AsyncHttpClient httpClient;

    private final Map<String, Trade> trades = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> states = new ConcurrentHashMap<>();
    private final Map<String, OrderResponse> orders = new ConcurrentHashMap<>();

    public TradeServiceImpl(AppConfig config, AsyncHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;

        config.getTrades().forEach(trade -> {
            trades.putIfAbsent(trade.getProductId(), trade);
            states.putIfAbsent(trade.getProductId(), new AtomicInteger(0));
        });
    }

    @Override
    public void process(Quote quote, Consumer<String> unsubscribe) {
        Trade trade = trades.get(quote.getBody().getSecurityId());
        AtomicInteger state = states.get(trade.getProductId());

        int curState = state.get();

        // No actions while in interstate
        if (curState == BUY_SENT || curState == SELL_SENT || curState == SOLD) {
            return;
        }

        // Check buy first
        if (isBuy(quote, trade) && state.compareAndSet(NEW, BUY_SENT)) {
            buy(trade);
            return;
        }

        // Check sell first
        if (isSell(quote, trade) && state.compareAndSet(BOUGHT, SELL_SENT)) {
            sell(trade, unsubscribe);
        }
    }

    private void buy(Trade trade) {
        ListenableFuture<Response> responseFuture = httpClient.executeRequest(
                prepareBuyRequest(config, Trade.builder()
                        .productId(trade.getProductId())
                        .amount(trade.getAmount())
                        .leverage(trade.getLeverage())
                        .build()));

        responseFuture.addListener(() -> {
            try {
                Response response = responseFuture.get();

                if (response.getStatusCode() == 200) {
                    OrderResponse orderResponse = getObject(response.getResponseBody(), OrderResponse.class);

                    orders.putIfAbsent(trade.getProductId(), orderResponse);
                    states.get(trade.getProductId()).compareAndSet(BUY_SENT, BOUGHT);

                    log.info("BUY SUCCESS. Response: {}", orderResponse);
                } else {
                    log.error("BUY FAILED. Response: {}. Product: {}", response.toString(), trade.getProductId());
                    states.get(trade.getProductId()).compareAndSet(BUY_SENT, NEW);
                }
            } catch (Throwable t) {
                log.error("Exception occurred while executing buy request", t);
                states.get(trade.getProductId()).compareAndSet(BUY_SENT, NEW);
            }
        }, null);
    }

    private void sell(Trade trade, Consumer<String> unsubscribe) {
        OrderResponse order = orders.get(trade.getProductId());

        ListenableFuture<Response> responseFuture = httpClient.executeRequest(
                prepareSellRequest(config, order.getPositionId()));

        responseFuture.addListener(() -> {
            try {
                Response response = responseFuture.get();

                if (response.getStatusCode() == 200) {
                    OrderResponse orderResponse = getObject(response.getResponseBody(), OrderResponse.class);

                    orders.put(trade.getProductId(), orderResponse);
                    states.get(trade.getProductId()).compareAndSet(SELL_SENT, SOLD);

                    log.info("SELL SUCCESS. Response: {}", orderResponse);

                    try {
                        unsubscribe.accept(trade.getProductId());
                    } catch (Throwable t) {
                        log.error(String.format("Can not unsubscribe from '%s'", trade.getProductId()), t);
                    }
                } else {
                    log.error("SELL FAILED. Response: {}. Order: {}", response.toString(), order);
                    states.get(trade.getProductId()).compareAndSet(SELL_SENT, BOUGHT);
                }
            } catch (Throwable t) {
                log.error("Exception occurred while executing sell request", t);
                states.get(trade.getProductId()).compareAndSet(SELL_SENT, BOUGHT);
            }
        }, null);
    }

    private boolean isBuy(Quote quote, Trade trade) {
        return trade.getBuyPrice() >= quote.getBody().getCurrentPrice();
    }

    private boolean isSell(Quote quote, Trade trade) {
        return quote.getBody().getCurrentPrice() <= trade.getStopLossPrice() ||
                trade.getTakeProfitPrice() <= quote.getBody().getCurrentPrice();
    }
}
