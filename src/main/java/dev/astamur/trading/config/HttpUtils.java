package dev.astamur.trading.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.astamur.trading.model.*;
import io.netty.handler.codec.http.*;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.uri.Uri;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class HttpUtils {
    // Paths
    public static final String SUBSCRIPTION_PATH = "/subscriptions/me";
    public static final String BUY_PATH = "/core/21/users/me/trades";
    public static final String SELL_PATH = "/core/21/users/me/portfolio/positions/%s";

    public static final int MAX_CONTENT_LENGTH = 1024 * 1024;

    public static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    public static HttpHeaders getSubscriptionHeaders(String token) {
        return new DefaultHttpHeaders()
                .add(HttpHeaderNames.AUTHORIZATION, "Bearer " + token)
                .add(HttpHeaderNames.ACCEPT_LANGUAGE, "nl-NL,en;q=0.8");
    }

    public static HttpHeaders getOrderHeaders(String host, String token) {
        return new DefaultHttpHeaders()
                .add(HttpHeaderNames.HOST, host)
                .add(HttpHeaderNames.AUTHORIZATION, "Bearer " + token)
                .add(HttpHeaderNames.ACCEPT_LANGUAGE, "nl-NL,en;q=0.8")
                .add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                .add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
    }

    public static <T> byte[] getBytes(T value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Can't serialize object to json", e);
        }
    }

    public static <T> T getObject(String source, Class<T> tClass) {
        try {
            return mapper.readValue(source, tClass);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Can't deserialize json string to object", e);
        }
    }

    public static String getString(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Can't serialize json value to string", e);
        }
    }

    public static Request prepareBuyRequest(AppConfig config, Trade trade) {
        byte[] buyOrder = getBytes(OrderRequest.builder()
                .productId(trade.getProductId())
                .investingAmount(Amount.builder()
                        .currency("BUX")
                        .decimals(1)
                        .amount(Double.toString(trade.getAmount()))
                        .build())
                .leverage(trade.getLeverage())
                .direction(DirectionType.BUY)
                .source(Source.builder()
                        .sourceType(SourceType.OTHER)
                        .build())
                .build());

        RequestBuilder builder = new RequestBuilder()
                .setMethod(HttpMethod.POST.name())
                .setUri(Uri.create(config.getOrderUri().resolve(BUY_PATH).toString()))
                .setHeaders(getOrderHeaders(config.getOrderUri().getHost(), config.getOrderToken()))
                .setBody(buyOrder)
                .setHeader(HttpHeaderNames.CONTENT_LENGTH, buyOrder.length);

        return builder.build();
    }

    public static Request prepareSellRequest(AppConfig config, String positionId) {
        return new RequestBuilder()
                .setMethod(HttpMethod.DELETE.name())
                .setUrl(config.getOrderUri().resolve(String.format(SELL_PATH, positionId)).toString())
                .setHeaders(getOrderHeaders(config.getOrderUri().getHost(), config.getOrderToken()))
                .build();
    }

    public static SubscriptionRequest subscribeTo(List<String> productIds) {
        return SubscriptionRequest.builder()
                .subscribeTo(toListIds(productIds))
                .build();
    }

    public static int getPort(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return -1;
        }
        return uri.getPort() == -1
                ? "http".equals(uri.getScheme()) ? 80 : 443
                : uri.getPort();
    }

    public static SubscriptionRequest unsubscribeFrom(List<String> productIds) {
        return SubscriptionRequest.builder()
                .unsubscribeFrom(toListIds(productIds))
                .build();
    }

    private static List<String> toListIds(List<String> productIds) {
        return productIds.stream()
                .map(id -> "trading.product." + id)
                .collect(Collectors.toList());
    }
}
