package dev.astamur.trading.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum QuoteType {
    CONNECTED("connect.connected"),
    FAILED("connect.failed"),
    PERFORMANCE("portfolio.performance"),
    PROMOTION_BANNER("incentives.promotion.banner"),
    CLOSED("portfolio.position.closed"),
    QUOTE("trading.quote");

    private String typeName;

    QuoteType(String typeName) {
        this.typeName = typeName;
    }

    @JsonValue
    public String getTypeName() {
        return typeName;
    }
}
