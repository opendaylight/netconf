/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import io.netty.handler.codec.ByteToMessageDecoder;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * A decoder from a series of bytes to a {@link NetconfMessage}.
 */
// FIXME: 'NetconfMesssage' is not quite accurate: we can also produce Exceptions and the handling of those needs to be
//        specific to the negotiator or attached client/server session. This class should probably be producting:
//        -  sealed interface MessageEvent permits MessageException, MessageDocument
//        -  record DecoderError(Exception cause), or similar
//        -  record DecoderMessage(Document document), to be converted to NetconfMessage
//        There should be another abstract class for the above:
//        -  abstract sealed class MessageHandler extends ChannelInboundHandlerAdapter
//               permits AbstractNetconfSesssion, AbstractSessionNegotiator
//        -  with it having a codified channelRead() method which does not tolerate unknown messages and dispatches to
//           the abstract equivalent of AbstractNetconfSesssion.handleError()/handleMessage()
public abstract class MessageDecoder extends ByteToMessageDecoder {
    /**
     * The name of the handler providing message decoding.
     */
    public static final @NonNull String HANDLER_NAME = "netconfMessageDecoder";
}
