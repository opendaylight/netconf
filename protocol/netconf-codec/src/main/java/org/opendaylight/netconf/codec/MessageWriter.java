/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * An entity capable of writing {@link NetconfMessage}s into an {@link OutputStream}.
 */
@NonNullByDefault
public abstract class MessageWriter {
    private final boolean pretty;

    protected MessageWriter(final boolean pretty) {
        this.pretty = pretty;
    }

    public final void writeMessage(final NetconfMessage message, final OutputStream out) throws Exception {
        writeMessage(requireNonNull(message),
            pretty ? ThreadLocalTransformers.getPrettyTransformer() : ThreadLocalTransformers.getDefaultTransformer(),
            requireNonNull(out));
    }

    protected abstract void writeMessage(NetconfMessage message, Transformer transformer, OutputStream out)
        throws IOException, TransformerException;
}
