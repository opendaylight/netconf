/*
 * Copyright (c) 2016 Cisco Systems, Inc, Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import org.apache.sshd.SshClient;
import org.apache.sshd.common.future.DefaultSshFuture;
import org.apache.sshd.common.io.IoConnectFuture;
import org.apache.sshd.common.io.IoConnector;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.mina.MinaServiceFactory;
import org.apache.sshd.common.io.nio2.Nio2Connector;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeAsyncSshHandler extends AsyncSshHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeAsyncSshHandler.class);

    public static CallHomeAsyncSshHandler createForNetconfSubsystem(final AuthenticationHandler authenticationHandler,
                                                                    final IoSession tcpSession) throws IOException {
        LOG.warn("Create CallHomeAsyncSshHandler");
        return new CallHomeAsyncSshHandler(authenticationHandler, DEFAULT_CLIENT, tcpSession);
    }

    public CallHomeAsyncSshHandler(final AuthenticationHandler authenticationHandler,
                                   final SshClient sshClient,
                                   final IoSession tcpSession) throws IOException {
        super(authenticationHandler, sshClient);

        sshClient.setIoServiceFactoryFactory(manager -> new MinaServiceFactory(manager) {
            @Override
            public IoConnector createConnector(final IoHandler handler) {
                try {
                    return new Nio2Connector(manager, handler, AsynchronousChannelGroup.withThreadPool(MoreExecutors.newDirectExecutorService())) {
                        @Override
                        public IoConnectFuture connect(final SocketAddress address) {
                            DefaultIoConnectFuture defaultIoConnectFuture = new DefaultIoConnectFuture(null);
                            defaultIoConnectFuture.setSession(tcpSession);
                            return defaultIoConnectFuture;
                        }
                    };
                } catch (IOException e) {
                    // FIXME
                    e.printStackTrace();
                }
                return null;
            }
        });
    }

    static class DefaultIoConnectFuture extends DefaultSshFuture<IoConnectFuture> implements IoConnectFuture {
        DefaultIoConnectFuture(Object lock) {
            super(lock);
        }

        @Override
        public IoSession getSession() {
            Object v = getValue();
            return v instanceof IoSession ? (IoSession) v : null;
        }

        @Override
        public Throwable getException() {
            Object v = getValue();
            return v instanceof Throwable ? (Throwable) v : null;
        }

        @Override
        public boolean isConnected() {
            return getValue() instanceof IoSession;
        }

        @Override
        public void setSession(IoSession session) {
            setValue(session);
        }

        @Override
        public void setException(Throwable exception) {
            setValue(exception);
        }
    }
}