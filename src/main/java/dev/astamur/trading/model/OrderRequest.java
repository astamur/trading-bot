package dev.astamur.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String productId;
    private Amount investingAmount;
    private int leverage;
    private DirectionType direction;
    private Source source;
}
