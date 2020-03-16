package dev.astamur.trading.service;

import dev.astamur.trading.model.OrderResponse;
import dev.astamur.trading.model.Quote;

import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface TradeService {
    Future<OrderResponse> process(Quote quote, Consumer<String> unsubscribe);
}
