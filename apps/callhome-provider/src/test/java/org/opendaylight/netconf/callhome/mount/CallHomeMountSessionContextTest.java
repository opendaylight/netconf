/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.InetAddresses;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;

public class CallHomeMountSessionContextTest {
    private Inet4Address someAddressIpv4;
    private InetSocketAddress someSocketAddress;
    private CallHomeChannelActivator mockActivator;
    private CallHomeMountSessionContext.CloseCallback mockCallback;
    private CallHomeMountSessionContext instance;
    private CallHomeProtocolSessionContext mockProtocol;

    @Before
    public void setup() {
        someAddressIpv4 = (Inet4Address) InetAddresses.forString("1.2.3.4");
        someSocketAddress = new InetSocketAddress(someAddressIpv4, 123);

        mockProtocol = mock(CallHomeProtocolSessionContext.class);
        mockActivator = mock(CallHomeChannelActivator.class);
        mockCallback = mock(CallHomeMountSessionContext.CloseCallback.class);
        doReturn(someSocketAddress).when(mockProtocol).getRemoteAddress();
        doReturn(Name.SSH).when(mockProtocol).getTransportType();

        instance = new CallHomeMountSessionContext("test",mockProtocol, mockActivator, mockCallback);
    }

    @Test
    public void configNodeCanBeCreated() {
        assertNotNull(instance.getConfigNode());
    }

    @Test
    public void activationOfListenerSupportsSessionUp() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
            .thenAnswer(invocationOnMock -> {
                NetconfClientSession mockSession = mock(NetconfClientSession.class);

                Object arg = invocationOnMock.getArguments()[0];
                ((NetconfClientSessionListener) arg).onSessionUp(mockSession);
                return null;
            });

        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onSessionUp(any(NetconfClientSession.class));
    }

    @Test
    public void activationOfListenerSupportsSessionTermination() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
                .thenAnswer(invocationOnMock -> {
                    NetconfClientSession mockSession = mock(NetconfClientSession.class);
                    NetconfTerminationReason mockReason = mock(NetconfTerminationReason.class);

                    Object arg = invocationOnMock.getArguments()[0];
                    ((NetconfClientSessionListener) arg).onSessionTerminated(mockSession, mockReason);
                    return null;
                });

        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onSessionTerminated(any(NetconfClientSession.class),
                any(NetconfTerminationReason.class));
    }

    @Test
    public void activationOfListenerSupportsSessionDown() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
                .thenAnswer(invocationOnMock -> {
                    NetconfClientSession mockSession = mock(NetconfClientSession.class);
                    Exception mockException = mock(Exception.class);

                    Object arg = invocationOnMock.getArguments()[0];
                    ((NetconfClientSessionListener) arg).onSessionDown(mockSession, mockException);
                    return null;
                });
        // given
        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onSessionDown(any(NetconfClientSession.class),
                any(Exception.class));
    }

    @Test
    public void activationOfListenerSupportsSessionMessages() {
        // given
        when(mockActivator.activate(any(NetconfClientSessionListener.class)))
                .thenAnswer(invocationOnMock -> {
                    NetconfClientSession mockSession = mock(NetconfClientSession.class);
                    NetconfMessage mockMsg = mock(NetconfMessage.class);

                    Object arg = invocationOnMock.getArguments()[0];
                    ((NetconfClientSessionListener) arg).onMessage(mockSession, mockMsg);
                    return null;
                });
        // given
        NetconfClientSessionListener mockListener = mock(NetconfClientSessionListener.class);
        // when
        mockActivator.activate(mockListener);
        // then
        verify(mockListener, times(1)).onMessage(any(NetconfClientSession.class),
                any(NetconfMessage.class));
    }
}
