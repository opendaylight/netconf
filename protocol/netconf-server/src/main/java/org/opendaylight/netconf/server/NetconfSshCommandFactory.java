/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.shaded.sshd.server.Environment;
import org.opendaylight.netconf.shaded.sshd.server.ExitCallback;
import org.opendaylight.netconf.shaded.sshd.server.channel.ChannelSession;
import org.opendaylight.netconf.shaded.sshd.server.command.Command;
import org.opendaylight.netconf.shaded.sshd.server.subsystem.SubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfSshCommandFactory implements SubsystemFactory {

    public static NetconfSshCommandFactory INSTANCE = new NetconfSshCommandFactory();
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSshCommandFactory.class);

    private NetconfSshCommandFactory(){
        // singleton
    }

    @Override
    public String getName() {
        return "netconf";
    }

    @Override
    public Command createSubsystem(ChannelSession channel) throws IOException {
        return new NetconfSshCommand();
    }

    private static class NetconfSshCommand implements Command {

        private ExitCallback callback;

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            // Do nothing
        }

        @Override
        public void setInputStream(InputStream in) {
            // Do nothing
        }

        @Override
        public void setOutputStream(OutputStream out) {
            // Do nothing
        }

        @Override
        public void start(ChannelSession channel, Environment env) throws IOException {
            final var session = channel.getServerSession();
            final var address = (InetSocketAddress) session.getClientAddress();
            final var header = new NetconfHelloMessageAdditionalHeader(
                session.getUsername(), address.getHostName(), String.valueOf(address.getPort()), "ssh", "client"
            ).toFormattedString();
            LOG.info(header);
           // channel.handleData(new ByteArrayBuffer(header.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public void destroy(ChannelSession channel) throws Exception {
            if (callback != null) {
                callback.onExit(0);
            }
        }
    }
}
