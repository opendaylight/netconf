/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.NetconfExiSession;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.UnsupportedOption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AbstractNetconfSession} which is also a {@link NetconfExiSession}.
 */
public abstract class AbstractNetconfExiSession<
        S extends AbstractNetconfExiSession<S, L>,
        L extends NetconfSessionListener<S>> extends AbstractNetconfSession<S, L> implements NetconfExiSession {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfExiSession.class);

    protected AbstractNetconfExiSession(final L sessionListener, final Channel channel, final SessionIdType sessionId) {
        super(sessionListener, channel, sessionId);
    }

    @Override
    public final void startExiCommunication(final NetconfMessage startExiMessage) {
        final EXIParameters exiParams;
        try {
            exiParams = EXIParameters.fromXmlElement(XmlElement.fromDomDocument(startExiMessage.getDocument()));
        } catch (final UnsupportedOption e) {
            LOG.warn("Unable to parse EXI parameters from {} on session {}", startExiMessage, this, e);
            throw new IllegalArgumentException("Cannot parse options", e);
        }

        final var exiCodec = NetconfEXICodec.forParameters(exiParams);
        final var exiWriter = exiCodec.newMessageWriter();
        final MessageDecoder exiDecoder;
        try {
            exiDecoder = exiCodec.newMessageDecoder();
        } catch (EXIException e) {
            LOG.warn("Failed to instantiate EXI decodeer for {} on session {}", exiCodec, this, e);
            throw new IllegalStateException("Cannot instantiate encoder for options", e);
        }

        addExiHandlers(exiDecoder, exiWriter);
        LOG.debug("Session {} EXI handlers added to pipeline", this);
    }

    /**
     * Add a set encoder/decoder tuple into the channel pipeline as appropriate.
     *
     * @param decoder EXI decoder
     * @param encoder EXI {@link MessageWriter}
     */
    @NonNullByDefault
    protected abstract void addExiHandlers(MessageDecoder decoder, MessageWriter encoder);

    @NonNullByDefault
    protected final void replaceMessageDecoder(final MessageDecoder newDecoder) {
        replaceChannelHandler(MessageDecoder.class, MessageDecoder.HANDLER_NAME, newDecoder);
    }

    @NonNullByDefault
    protected final void setMessageWriter(final MessageWriter newWriter) {
        messageEncoder().setWriter(newWriter);
    }

    @NonNullByDefault
    protected final void setMessageWriterAfterNextMessage(final MessageWriter newWriter) {
        messageEncoder().setNextWriter(newWriter);
    }
}
