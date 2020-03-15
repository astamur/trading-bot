package dev.astamur.trading;

import dev.astamur.trading.client.SubscriptionClientHandler;
import dev.astamur.trading.client.SubscriptionClientInitializer;
import dev.astamur.trading.config.AppConfig;
import dev.astamur.trading.service.TradeServiceImpl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;

import java.io.IOException;

import static dev.astamur.trading.config.HttpUtils.getPort;
import static org.asynchttpclient.Dsl.asyncHttpClient;

@Slf4j
public class App {
    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        AsyncHttpClient httpClient = asyncHttpClient();

        new App().start(config, httpClient);
    }

    public void start(AppConfig config, AsyncHttpClient httpClient) {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            SubscriptionClientHandler subscriptionHandler =
                    new SubscriptionClientHandler(config, new TradeServiceImpl(config, httpClient));

            // Start subscription client
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new SubscriptionClientInitializer(config, subscriptionHandler));

            Channel subscriptionChannel = bootstrap
                    .connect(config.getSubscriptionUri().getHost(), getPort(config.getSubscriptionUri()))
                    .sync()
                    .channel();
            subscriptionHandler.handshakeFuture().sync();
            subscriptionChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Application has been interrupted", e);
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error("Error occurred while closing http client");
            }
            group.shutdownGracefully();
        }
    }
}