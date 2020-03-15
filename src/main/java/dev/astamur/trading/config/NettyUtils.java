package dev.astamur.trading.config;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;

public class NettyUtils {
    public static SslContext initSslContext(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return null;
        }

        try {
            return "https".equalsIgnoreCase(uri.getScheme())
                    ? SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                    : null;
        } catch (SSLException e) {
            throw new IllegalStateException("Can't instantiate SSL context", e);
        }
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
