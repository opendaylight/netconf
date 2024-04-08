/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * A body which is capable of being formatted to an {@link OutputStream} in either JSON or XML format using particular
 * {@link FormatParameters}.
 */
@NonNullByDefault
public abstract class FormattableBody implements Immutable {
    /**
     * Write the content of this body as a JSON document.
     *
     * @param format {@link FormatParameters}
     * @param out output stream
     * @throws IOException if an IO error occurs.
     */
    public abstract void formatToJSON(FormatParameters format, OutputStream out) throws IOException;

    /**
     * Write the content of this body as an XML document.
     *
     * @param format {@link FormatParameters}
     * @param out output stream
     * @throws IOException if an IO error occurs.
     */
    public abstract void formatToXML(FormatParameters format, OutputStream out) throws IOException;

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }
}
