package dev.astamur.trading.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteBody {
    String securityId;
    String developerMessage;
    String errorCode;
    Long timeStamp;
    Double currentPrice;
}
