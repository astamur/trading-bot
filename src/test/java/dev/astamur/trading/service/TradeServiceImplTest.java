package dev.astamur.trading.service;

import dev.astamur.trading.config.AppConfig;
import dev.astamur.trading.model.*;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.asynchttpclient.Response;
import org.asynchttpclient.test.TestUtils;
import org.asynchttpclient.testserver.HttpServer;
import org.asynchttpclient.testserver.HttpTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static dev.astamur.trading.config.HttpUtils.getString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.test.TestUtils.TIMEOUT;
import static org.asynchttpclient.test.TestUtils.writeResponseBody;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeServiceImplTest extends HttpTest {
    private static final String PRODUCT_ID = "test";
    private static final String POSITION_ID = UUID.randomUUID().toString();

    private static HttpServer server;

    @BeforeAll
    public static void start() throws Throwable {
        server = new HttpServer();
        server.start();
    }

    @AfterAll
    public static void stop() throws Throwable {
        server.close();
    }

    @Test
    public void getRootUrl() throws Throwable {
        withClient().run(client ->
                withServer(server).run(server -> {
                    String url = server.getHttpUrl();
                    server.enqueueOk();

                    Response response = client.prepareGet(url).execute(new TestUtils.AsyncCompletionHandlerAdapter()).get(TIMEOUT, SECONDS);

                    assertThat(response.getUri().toUrl()).isEqualTo(url);
                }));
    }

    @Test
    public void shouldExecuteBuyOrder() throws Throwable {
        Trade trade = getTrade();
        Quote quote = getQuote(9.0);

        OrderResponse order = OrderResponse.builder()
                .positionId(POSITION_ID)
                .leverage(trade.getLeverage())
                .investingAmount(Amount.builder()
                        .amount(Double.toString(quote.getBody().getCurrentPrice()))
                        .decimals(1)
                        .currency("BUX")
                        .build())
                .build();

        AppConfig config = mock(AppConfig.class);

        when(config.getOrderUri()).thenReturn(URI.create(server.getHttpUrl()));
        when(config.getTrades()).thenReturn(List.of(trade));

        withClient().run(client -> {
            TradeServiceImpl tradeService = new TradeServiceImpl(config, client);

            withServer(server).run(server -> {
                server.enqueueResponse(response -> {
                    response.setStatus(200);
                    response.setContentType(HttpHeaderValues.APPLICATION_JSON.toString());
                    writeResponseBody(response, getString(order));
                });

                AtomicBoolean isUnsubscribed = new AtomicBoolean(false);

                assertThat(tradeService.process(quote, (positionId) -> isUnsubscribed.set(true)).get(30, SECONDS))
                        .isEqualTo(order);
                assertThat(isUnsubscribed.get()).isFalse();
            });
        });
    }

    @Test
    public void shouldSellWithProfit() throws Throwable {
        Quote buyQuote = getQuote(9.0);
        Quote sellQuote = getQuote(16.0);
        Trade trade = getTrade();

        OrderResponse.OrderResponseBuilder orderBuilder = OrderResponse.builder()
                .positionId(POSITION_ID)
                .leverage(trade.getLeverage())
                .investingAmount(Amount.builder()
                        .amount(Double.toString(buyQuote.getBody().getCurrentPrice()))
                        .decimals(1)
                        .currency("BUX")
                        .build());
        OrderResponse buyOrder = orderBuilder.build();
        OrderResponse sellOrder = orderBuilder
                .profitAndLoss(Amount.builder()
                        .amount(Double.toString(sellQuote.getBody().getCurrentPrice() -
                                buyQuote.getBody().getCurrentPrice()))
                        .decimals(1)
                        .currency("BUX")
                        .build())
                .build();

        AppConfig config = mock(AppConfig.class);

        when(config.getOrderUri()).thenReturn(URI.create(server.getHttpUrl()));
        when(config.getTrades()).thenReturn(List.of(trade));

        withClient().run(client -> {
            TradeServiceImpl tradeService = new TradeServiceImpl(config, client);

            withServer(server).run(server -> {
                server.enqueueResponse(response -> {
                    response.setStatus(200);
                    response.setContentType(HttpHeaderValues.APPLICATION_JSON.toString());
                    writeResponseBody(response, getString(buyOrder));
                });

                server.enqueueResponse(response -> {
                    response.setStatus(200);
                    response.setContentType(HttpHeaderValues.APPLICATION_JSON.toString());
                    writeResponseBody(response, getString(sellOrder));
                });

                AtomicBoolean isUnsubscribed = new AtomicBoolean(false);
                Consumer<String> unsubscribeAction = positionId -> isUnsubscribed.set(true);

                assertThat(tradeService.process(buyQuote, unsubscribeAction).get(30, SECONDS)).isEqualTo(buyOrder);
                assertThat(isUnsubscribed.get()).isFalse();

                assertThat(tradeService.process(sellQuote, unsubscribeAction).get(30, SECONDS)).isEqualTo(sellOrder);
                assertThat(isUnsubscribed.get()).isTrue();
            });
        });
    }

    private Quote getQuote(double price) {
        return Quote.builder()
                .type(QuoteType.QUOTE)
                .body(QuoteBody.builder()
                        .securityId(PRODUCT_ID)
                        .currentPrice(price)
                        .timeStamp(System.currentTimeMillis())
                        .build())
                .build();
    }

    private Trade getTrade() {
        return Trade.builder()
                .productId(PRODUCT_ID)
                .leverage(1)
                .stopLossPrice(5)
                .buyPrice(10)
                .takeProfitPrice(15)
                .build();
    }
}