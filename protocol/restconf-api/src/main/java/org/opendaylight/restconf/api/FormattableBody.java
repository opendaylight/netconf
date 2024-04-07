/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * A body which is capable of being formatted to an {@link OutputStream} in either JSON or XML format.
 */
@NonNullByDefault
public abstract class FormattableBody implements Immutable {
    private final FormatParameters format;

    protected FormattableBody(final FormatParameters format) {
        this.format = requireNonNull(format);
    }

    /**
     * Write the content of this body as a JSON document.
     *
     * @param out output stream
     * @throws IOException if an IO error occurs.
     */
    public final void formatToJSON(final OutputStream out) throws IOException {
        formatToJSON(requireNonNull(out), format);
    }

    protected abstract void formatToJSON(OutputStream out, FormatParameters format) throws IOException;

    /**
     * Write the content of this body as an XML document.
     *
     * @param out output stream
     * @throws IOException if an IO error occurs.
     */
    public final void formatToXML(final OutputStream out) throws IOException {
        formatToXML(requireNonNull(out), format);
    }

    protected abstract void formatToXML(OutputStream out, FormatParameters format) throws IOException;

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("prettyPrint", format.prettyPrint().value());
    }
}
