package org.opendaylight.netconf.client.conf;

import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import org.apache.mina.core.session.IoSession;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.protocol.framework.NeverReconnectStrategy;

public class NetconfReversedClientConfiguration extends NetconfClientConfiguration {

    private final IoSession tcpSession;

    public NetconfReversedClientConfiguration(final Long connectionTimeoutMillis,
                                              final NetconfHelloMessageAdditionalHeader additionalHeader,
                                              final NetconfClientSessionListener sessionListener,
                                              final AuthenticationHandler authHandler, final IoSession tcpSession) {
        // FIXME the address has to be unused
        super(NetconfClientProtocol.SSH, InetSocketAddress.createUnresolved("0.0.0.0.", 8833),
                connectionTimeoutMillis, additionalHeader, sessionListener,
                new NeverReconnectStrategy(GlobalEventExecutor.INSTANCE, 10000), authHandler);
        this.tcpSession = tcpSession;
    }

    public IoSession getTcpSession() {
        return tcpSession;
    }
}
