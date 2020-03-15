package dev.astamur.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String id;
    private String positionId;
    private Amount profitAndLoss;
    private Product product;
    private Amount investingAmount;
    private Amount price;
    private int leverage;
    private DirectionType direction;
    private String type;
    private long dateCreated;
}
