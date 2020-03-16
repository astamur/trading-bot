package dev.astamur.trading.service;

import dev.astamur.trading.config.AppConfig;
import dev.astamur.trading.model.*;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.testserver.HttpServer;
import org.asynchttpclient.testserver.HttpTest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static dev.astamur.trading.config.HttpUtils.getString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.test.TestUtils.writeResponseBody;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeServiceImplTest extends HttpTest {
    private static final String PRODUCT_ID = "test";
    private static final String POSITION_ID = UUID.randomUUID().toString();
    private static final int TIMEOUT = 5;

    private static Random rand = new Random(System.currentTimeMillis());
    private static HttpServer server;

    private AppConfig config = mock(AppConfig.class);

    @BeforeAll
    public static void start() throws Throwable {
        server = new HttpServer();
        server.start();
    }

    @AfterAll
    public static void stop() throws Throwable {
        server.close();
    }

    @BeforeEach
    public void init() {
        when(config.getOrderUri()).thenReturn(URI.create(server.getHttpUrl()));
    }

    @Test
    public void shouldBuyOrder() throws Throwable {
        Trade trade = getTrade();
        Quote quote = getQuote(9.0);
        OrderResponse order = getBuyOrder(quote);

        when(config.getTrades()).thenReturn(List.of(trade));

        withClient().run(client -> {
            TradeServiceImpl tradeService = new TradeServiceImpl(config, client);

            withServer(server).run(server -> {
                server.enqueueResponse(getOrderResponse(order));

                assertThat(tradeService.process(quote, id -> {/**/}).get(TIMEOUT, SECONDS)).isEqualTo(order);
            });
        });
    }

    @Test
    public void shouldSell() throws Throwable {
        Quote buyQuote = getQuote(9.0);
        Quote sellQuote = getQuote(16.0);
        Trade trade = getTrade();

        OrderResponse buyOrder = getBuyOrder(buyQuote);
        OrderResponse sellOrder = getSellOrder(buyOrder, sellQuote.getBody().getCurrentPrice() -
                buyQuote.getBody().getCurrentPrice());

        when(config.getTrades()).thenReturn(List.of(trade));

        withClient().run(client -> {
            TradeServiceImpl tradeService = new TradeServiceImpl(config, client);

            withServer(server).run(server -> {
                server.enqueueResponse(getOrderResponse(buyOrder));
                server.enqueueResponse(getOrderResponse(sellOrder));

                AtomicBoolean isUnsubscribed = new AtomicBoolean(false);
                Consumer<String> unsubscribeAction = positionId -> isUnsubscribed.set(true);

                // Successful buy
                assertThat(tradeService.process(buyQuote, unsubscribeAction).get(TIMEOUT, SECONDS)).isEqualTo(buyOrder);
                assertThat(isUnsubscribed.get()).isFalse();

                // Successful sell
                assertThat(tradeService.process(sellQuote, unsubscribeAction).get(TIMEOUT, SECONDS)).isEqualTo(sellOrder);
                assertThat(isUnsubscribed.get()).isTrue();

                // Null after sell
                assertThat(tradeService.process(sellQuote, unsubscribeAction).get(TIMEOUT, SECONDS)).isNull();
            });
        });
    }

    @Test
    public void shouldBuyAfterFail() throws Throwable {
        Trade trade = getTrade();
        Quote quote = getQuote(9.0);
        OrderResponse buyOrder = getBuyOrder(quote);

        when(config.getTrades()).thenReturn(List.of(trade));

        withClient().run(client -> {
            TradeServiceImpl tradeService = new TradeServiceImpl(config, client);

            withServer(server).run(server -> {
                server.enqueue(getFailedHandler());
                server.enqueueResponse(getBadResponse());
                server.enqueueResponse(getOrderResponse(buyOrder));

                // 500 - Error on a server
                assertThat(tradeService.process(quote, id -> {/**/}).get(TIMEOUT, SECONDS)).isNull();
                // 400 - Bad request
                assertThat(tradeService.process(quote, id -> {/**/}).get(TIMEOUT, SECONDS)).isNull();
                // Successful buy
                assertThat(tradeService.process(quote, id -> {/**/}).get(TIMEOUT, SECONDS)).isEqualTo(buyOrder);
            });
        });
    }

    @Test
    public void shouldSellAfterFail() throws Throwable {
        Quote buyQuote = getQuote(9.0);
        Quote sellQuote = getQuote(16.0);
        Trade trade = getTrade();

        OrderResponse buyOrder = getBuyOrder(buyQuote);
        OrderResponse sellOrder = getSellOrder(buyOrder, sellQuote.getBody().getCurrentPrice() -
                buyQuote.getBody().getCurrentPrice());

        when(config.getTrades()).thenReturn(List.of(trade));

        withClient().run(client -> {
            TradeServiceImpl tradeService = new TradeServiceImpl(config, client);

            withServer(server).run(server -> {
                server.enqueueResponse(getOrderResponse(buyOrder));
                // A bad request in the middle
                server.enqueueResponse(getBadResponse());
                server.enqueueResponse(getOrderResponse(sellOrder));

                AtomicBoolean isUnsubscribed = new AtomicBoolean(false);
                Consumer<String> unsubscribeAction = positionId -> isUnsubscribed.set(true);

                // Successful buy
                assertThat(tradeService.process(buyQuote, unsubscribeAction).get(TIMEOUT, SECONDS)).isEqualTo(buyOrder);
                assertThat(isUnsubscribed.get()).isFalse();

                // 400 - Bad request in the middle
                assertThat(tradeService.process(sellQuote, id -> {/**/}).get(TIMEOUT, SECONDS)).isNull();

                // Successful sell
                assertThat(tradeService.process(sellQuote, unsubscribeAction).get(TIMEOUT, SECONDS)).isEqualTo(sellOrder);
                assertThat(isUnsubscribed.get()).isTrue();
            });
        });
    }


    @Test
    public void shouldReturnNullWhenNoTrades() throws Throwable {
        when(config.getTrades()).thenReturn(Collections.emptyList());

        TradeServiceImpl tradeService = new TradeServiceImpl(config, mock(AsyncHttpClient.class));
        assertThat(tradeService.process(randomQuote(), (positionId) -> {/**/}).get(TIMEOUT, SECONDS)).isNull();

    }

    @Test
    public void shouldReturnNullWhenTradeNotFound() throws Throwable {
        when(config.getTrades()).thenReturn(List.of(getTrade()));

        TradeServiceImpl tradeService = new TradeServiceImpl(config, mock(AsyncHttpClient.class));
        assertThat(tradeService.process(randomQuote(), (positionId) -> {/**/}).get(TIMEOUT, SECONDS)).isNull();

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

    private Quote randomQuote() {
        return Quote.builder()
                .type(QuoteType.QUOTE)
                .body(QuoteBody.builder()
                        .securityId(PRODUCT_ID + "_" + rand.nextInt(1000))
                        .currentPrice(rand.nextDouble() * 1000)
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

    private OrderResponse getBuyOrder(Quote quote) {
        return OrderResponse.builder()
                .positionId(POSITION_ID)
                .leverage(1)
                .investingAmount(Amount.builder()
                        .amount(Double.toString(quote.getBody().getCurrentPrice()))
                        .decimals(1)
                        .currency("BUX")
                        .build())
                .build();
    }

    private OrderResponse getSellOrder(OrderResponse buyOrder, double profit) {
        return OrderResponse.builder()
                .positionId(buyOrder.getPositionId())
                .leverage(buyOrder.getLeverage())
                .investingAmount(Amount.builder()
                        .amount(buyOrder.getInvestingAmount().getAmount())
                        .decimals(buyOrder.getInvestingAmount().getDecimals())
                        .currency(buyOrder.getInvestingAmount().getCurrency())
                        .build())
                .profitAndLoss(Amount.builder()
                        .amount(Double.toString(profit))
                        .decimals(buyOrder.getInvestingAmount().getDecimals())
                        .currency(buyOrder.getInvestingAmount().getCurrency())
                        .build())
                .build();
    }

    public AbstractHandler getFailedHandler() {
        return new AbstractHandler() {
            @Override
            public void handle(String target, Request request, HttpServletRequest httpServletRequest,
                               HttpServletResponse httpServletResponse) {
                throw new RuntimeException("Something went wrong on a server");
            }
        };
    }

    public HttpServer.HttpServletResponseConsumer getBadResponse() {
        return response -> {
            response.setStatus(400);
            response.setContentType(HttpHeaderValues.APPLICATION_JSON.toString());
            writeResponseBody(response, getString("{\"msg\":\"Bad request\"}"));
        };
    }

    public HttpServer.HttpServletResponseConsumer getOrderResponse(OrderResponse order) {
        return response -> {
            response.setStatus(200);
            response.setContentType(HttpHeaderValues.APPLICATION_JSON.toString());
            writeResponseBody(response, getString(order));
        };
    }
}