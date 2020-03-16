package dev.astamur.trading.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote {
    @JsonProperty("t")
    QuoteType type;

    @JsonProperty("v")
    Integer version;

    String id;
    QuoteBody body;
}