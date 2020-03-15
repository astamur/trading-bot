package dev.astamur.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    private String productId;
    private double amount;
    private double stopLossPrice;
    private double buyPrice;
    private double takeProfitPrice;
    private int leverage;
}
