package dev.astamur.trading.client;

import dev.astamur.trading.config.AppConfig;
import dev.astamur.trading.model.Quote;
import dev.astamur.trading.model.QuoteType;
import dev.astamur.trading.model.SubscriptionRequest;
import dev.astamur.trading.model.Trade;
import dev.astamur.trading.service.TradeService;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

import static dev.astamur.trading.config.HttpUtils.*;

@Slf4j
public class SubscriptionClientHandler extends SimpleChannelInboundHandler<Object> {
    private final WebSocketClientHandshaker handshaker;
    private final TradeService tradeService;
    private final AppConfig config;

    private ChannelPromise handshakeFuture;


    public SubscriptionClientHandler(AppConfig config, TradeService tradeService) {
        this.config = config;
        this.handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(config.getSubscriptionUri().resolve(SUBSCRIPTION_PATH), WebSocketVersion.V13,
                        null, true, getSubscriptionHeaders(config.getSubscriptionToken()));
        this.tradeService = tradeService;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());

        handshakeFuture.addListener(future -> {
            if (future.isSuccess()) {
                ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(getBytes(
                        subscribeTo(config.getTrades().stream()
                                .map(Trade::getProductId)
                                .collect(Collectors.toList()))))));
            }
        });

        log.info("Subscription client connected to {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Subscription client disconnected!");
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                log.info("Subscription client connected!");
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                log.error("Subscription client failed to connect");
                handshakeFuture.setFailure(e);
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        try {
            if (frame instanceof TextWebSocketFrame) {
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                Quote quote = getObject(textFrame.text(), Quote.class);

                if (QuoteType.QUOTE == quote.getType()) {
                    log.info("QUOTE: " + quote);
                    tradeService.process(quote, productId -> ctx.writeAndFlush(new BinaryWebSocketFrame(
                            Unpooled.wrappedBuffer(getBytes(unsubscribeFrom(List.of(productId)))))));
                }
            } else if (frame instanceof CloseWebSocketFrame) {
                log.info("Closing subscription client");
                ch.close();
            }
        } catch (Throwable t) {
            log.error("Can't process quotes response", t);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error occurred while processing quotes", cause);

        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }

        ctx.close();
    }
}
