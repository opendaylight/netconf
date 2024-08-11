/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * An entity capable of writing {@link NetconfMessage}s into an {@link OutputStream}.
 */
@NonNullByDefault
public abstract class MessageWriter {
    /**
     * A transformer with default configuration.
     */
    @VisibleForTesting
    static final ThreadLocalTransformer DEFAULT_TRANSFORMER = new ThreadLocalTransformer(transformer -> {
        // No-op
    });

    /**
     * A transformer with default configuration, but with automatic indentation and the XML declaration removed.
     */
    @VisibleForTesting
    static final ThreadLocalTransformer PRETTY_TRANSFORMER = new ThreadLocalTransformer(transformer -> {
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    });

    private final ThreadLocalTransformer threadLocal;

    protected MessageWriter(final boolean pretty) {
        threadLocal = pretty ? PRETTY_TRANSFORMER : DEFAULT_TRANSFORMER;
    }

    protected final Transformer threadLocalTransformer() {
        return threadLocal.get();
    }

    @VisibleForTesting
    public final void writeMessage(final NetconfMessage message, final OutputStream out)
            throws IOException, TransformerException {
        writeTo(requireNonNull(message), requireNonNull(out));
    }

    protected abstract void writeTo(NetconfMessage message, OutputStream out) throws IOException, TransformerException;
}
