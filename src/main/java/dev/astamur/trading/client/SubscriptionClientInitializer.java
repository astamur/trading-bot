package dev.astamur.trading.client;

import dev.astamur.trading.config.AppConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.ssl.SslContext;

import static dev.astamur.trading.config.HttpUtils.MAX_CONTENT_LENGTH;
import static dev.astamur.trading.config.NettyUtils.initSslContext;

public class SubscriptionClientInitializer extends ChannelInitializer<SocketChannel> {
    private final AppConfig config;
    private final SubscriptionClientHandler handler;
    private final SslContext sslCtx;

    public SubscriptionClientInitializer(AppConfig config, SubscriptionClientHandler handler) {
        this.config = config;
        this.handler = handler;
        this.sslCtx = initSslContext(config.getSubscriptionUri());
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc(), config.getSubscriptionUri().getHost(),
                    config.getSubscriptionUri().getPort()));
        }

        pipeline.addLast("httpCodec", new HttpClientCodec())
                .addLast("httpAggregator", new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                .addLast("wsCompression", WebSocketClientCompressionHandler.INSTANCE)
                .addLast("jsonDecoder", new JsonObjectDecoder())
                .addLast("subscriptionHandler", handler);
    }
}