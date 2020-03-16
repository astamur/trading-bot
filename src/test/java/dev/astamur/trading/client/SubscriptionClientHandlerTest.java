package dev.astamur.trading.client;

import dev.astamur.trading.config.AppConfig;
import dev.astamur.trading.model.Quote;
import dev.astamur.trading.model.QuoteBody;
import dev.astamur.trading.model.QuoteType;
import dev.astamur.trading.service.TradeService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Base64;
import java.util.List;

import static dev.astamur.trading.config.HttpUtils.getString;
import static io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13.WEBSOCKET_13_ACCEPT_GUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testcontainers.shaded.org.apache.commons.codec.digest.DigestUtils.sha1;

class SubscriptionClientHandlerTest {
    private static final String SECURITY_ID = "test";

    private AppConfig config = mock(AppConfig.class);
    private TradeService tradeService = mock(TradeService.class);
    private EmbeddedChannel channel;

    @BeforeEach
    public void init() {
        when(config.getSubscriptionUri()).thenReturn(URI.create("https://localhost"));
        when(config.getSubscriptionToken()).thenReturn("token");

        channel = new EmbeddedChannel(new HttpResponseDecoder(), new SubscriptionClientHandler(config, tradeService));
        FullHttpRequest request = channel.readOutbound();
        channel.writeInbound(getHandshakeResponse(request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_KEY)));
    }

    @Test
    public void shouldProcessOnlyQuoteTypes() {
        Quote quoteWithPrice = getQuote();
        List.of(getQuoteOfType(QuoteType.CONNECTED),
                getQuoteOfType(QuoteType.FAILED),
                getQuoteOfType(QuoteType.PERFORMANCE),
                getQuoteOfType(QuoteType.PROMOTION_BANNER),
                getQuoteOfType(QuoteType.CLOSED),
                quoteWithPrice)
                .forEach(q -> channel.writeInbound(new TextWebSocketFrame(getString(q))));

        assertThat(channel.finish()).isTrue();

        verify(tradeService, times(1)).process(eq(quoteWithPrice), any());
    }

    private Quote getQuote() {
        return Quote.builder()
                .type(QuoteType.QUOTE)
                .body(QuoteBody.builder()
                        .securityId(SubscriptionClientHandlerTest.SECURITY_ID)
                        .currentPrice(10.)
                        .build())
                .build();
    }

    private Quote getQuoteOfType(QuoteType type) {
        return Quote.builder().type(type).build();
    }

    private FullHttpResponse getHandshakeResponse(String secret) {
        String acceptSeed = secret + WEBSOCKET_13_ACCEPT_GUID;
        byte[] sha1 = sha1(acceptSeed.getBytes(CharsetUtil.US_ASCII));
        String accept = Base64.getEncoder().encodeToString(sha1);

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
        response.headers().add(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
        response.headers().add(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, accept);

        return response;
    }
}