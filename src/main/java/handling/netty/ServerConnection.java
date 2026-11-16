package handling.netty;

/**
 * @author even from http://forum.ragezone.com/f427/netty-1079315/
 */
import constants.ServerConstants;
import java.util.concurrent.TimeUnit;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerConnection {
    private static final Logger LOG = LoggerFactory.getLogger(ServerConnection.class);

    private int port;
    private int world = -1;
    private int channels = -1;
    private ServerBootstrap boot;
    private EventLoopGroup bossGroup =
            new MultiThreadIoEventLoopGroup(
                    1,
                    NioIoHandler.newFactory()); // The initial connection thread where all the new
    // connections go to
    private EventLoopGroup workerGroup =
            new MultiThreadIoEventLoopGroup(
                    NioIoHandler
                            .newFactory()); // Once the connection thread has finished it will be
    // moved over to
    // this group where the thread will be managed
    private Channel channel;

    public ServerConnection(int port) {
        this.port = port;
    }

    /*
           public ServerConnection(int port, int channels) {
    	this.port = port;
    	this.channels = channels;
    }*/
    // Multiple worlds

    public ServerConnection(int port, int world, int channels) {
        this.port = port;
        this.world = world;
        this.channels = channels;
    }

    public void run() {
        try {
            boot =
                    new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .option(ChannelOption.SO_BACKLOG, ServerConstants.MAXIMUM_CONNECTIONS)
                            .childOption(ChannelOption.TCP_NODELAY, true)
                            .childOption(ChannelOption.SO_KEEPALIVE, true)
                            .childHandler(new ServerInitializer(channels));
            try {
                channel = boot.bind(port).sync().channel();
                LOG.info("Listening to port: {}", port);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("綁定 port " + port + " 失敗（執行緒被中斷）", e);
            } catch (Exception e) {
                throw new IllegalStateException("綁定 port " + port + " 失敗", e);
            }
        } catch (IllegalStateException e) {
            LOG.error("ServerConnection.run 綁定失敗，關閉 EventLoopGroup", e);
            close();
            throw e;
        } catch (Exception e) {
            LOG.error("ServerConnection.run 發生例外，關閉 EventLoopGroup", e);
            close();
            throw new IllegalStateException("啟動 ServerConnection 發生例外，port=" + port, e);
        }
    }

    public void close() {
        if (channel != null) {
            Channel ch = channel;
            channel = null;
            try {
                ch.close().await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        shutdownEventLoopGroup(bossGroup);
        shutdownEventLoopGroup(workerGroup);
    }

    private void shutdownEventLoopGroup(EventLoopGroup group) {
        try {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
