package dev.astamur.trading.service;

import dev.astamur.trading.model.Quote;

import java.util.function.Consumer;

public interface TradeService {
    void process(Quote quote, Consumer<String> unsubscribe);
}
