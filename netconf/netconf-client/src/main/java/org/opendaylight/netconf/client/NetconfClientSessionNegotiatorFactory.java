/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.exceptions.UnsupportedOption;
import io.netty.channel.Channel;
import io.netty.util.Timer;
import io.netty.util.concurrent.Promise;
import java.util.Set;
import org.opendaylight.netconf.api.NetconfClientSessionPreferences;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessage;
import org.opendaylight.protocol.framework.SessionListenerFactory;
import org.opendaylight.protocol.framework.SessionNegotiator;
import org.opendaylight.protocol.framework.SessionNegotiatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfClientSessionNegotiatorFactory implements SessionNegotiatorFactory<NetconfMessage,
        NetconfClientSession, NetconfClientSessionListener> {

    public static final Set<String> EXI_CLIENT_CAPABILITIES = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_EXI_1_0);

    public static final Set<String> LEGACY_EXI_CLIENT_CAPABILITIES = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_EXI_1_0);

    public static final Set<String> DEFAULT_CLIENT_CAPABILITIES = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);

    public static final Set<String> LEGACY_FRAMING_CLIENT_CAPABILITIES = ImmutableSet.of(
            XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0);

    private static final Logger LOG = LoggerFactory.getLogger(NetconfClientSessionNegotiatorFactory.class);
    private static final String START_EXI_MESSAGE_ID = "default-start-exi";
    private static final EXIParameters DEFAULT_OPTIONS;

    private final Optional<NetconfHelloMessageAdditionalHeader> additionalHeader;
    private final long connectionTimeoutMillis;
    private final Timer timer;
    private final EXIParameters options;

    static {
        final FidelityOptions fidelity = FidelityOptions.createDefault();
        try {
            fidelity.setFidelity(FidelityOptions.FEATURE_DTD, true);
            fidelity.setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, true);
            fidelity.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
        } catch (UnsupportedOption e) {
            LOG.warn("Failed to set fidelity options, continuing", e);
        }

        DEFAULT_OPTIONS = new EXIParameters(CodingMode.BYTE_PACKED, fidelity);
    }

    private final Set<String> clientCapabilities;

    public NetconfClientSessionNegotiatorFactory(final Timer timer,
                                                 final Optional<NetconfHelloMessageAdditionalHeader> additionalHeader,
                                                 final long connectionTimeoutMillis) {
        this(timer, additionalHeader, connectionTimeoutMillis, DEFAULT_OPTIONS);
    }

    public NetconfClientSessionNegotiatorFactory(final Timer timer,
                                                 final Optional<NetconfHelloMessageAdditionalHeader> additionalHeader,
                                                 final long connectionTimeoutMillis, final Set<String> capabilities) {
        this(timer, additionalHeader, connectionTimeoutMillis, DEFAULT_OPTIONS, capabilities);

    }

    public NetconfClientSessionNegotiatorFactory(final Timer timer,
                                                 final Optional<NetconfHelloMessageAdditionalHeader> additionalHeader,
                                                 final long connectionTimeoutMillis, final EXIParameters exiOptions) {
        this(timer, additionalHeader, connectionTimeoutMillis, exiOptions, EXI_CLIENT_CAPABILITIES);
    }

    public NetconfClientSessionNegotiatorFactory(final Timer timer,
                                                 final Optional<NetconfHelloMessageAdditionalHeader> additionalHeader,
                                                 final long connectionTimeoutMillis, final EXIParameters exiOptions,
                                                 final Set<String> capabilities) {
        this.timer = Preconditions.checkNotNull(timer);
        this.additionalHeader = additionalHeader;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.options = exiOptions;
        this.clientCapabilities = capabilities;
    }

    @Override
    public SessionNegotiator<NetconfClientSession> getSessionNegotiator(
            final SessionListenerFactory<NetconfClientSessionListener> sessionListenerFactory,
            final Channel channel, final Promise<NetconfClientSession> promise) {

        NetconfMessage startExiMessage = NetconfStartExiMessage.create(options, START_EXI_MESSAGE_ID);
        NetconfHelloMessage helloMessage = null;
        try {
            helloMessage = NetconfHelloMessage.createClientHello(clientCapabilities, additionalHeader);
        } catch (NetconfDocumentedException e) {
            LOG.error("Unable to create client hello message with capabilities {} and additional handler {}",
                    clientCapabilities, additionalHeader);
            throw new IllegalStateException(e);
        }

        NetconfClientSessionPreferences proposal = new NetconfClientSessionPreferences(helloMessage, startExiMessage);
        return new NetconfClientSessionNegotiator(proposal, promise, channel, timer,
                sessionListenerFactory.getSessionListener(), connectionTimeoutMillis);
    }
}
