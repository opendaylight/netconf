/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.FormattableBody;

/**
 * A {@link FormattableBody} which has an attached {@link DatabindContext}.
 */
@NonNullByDefault
public abstract class DatabindFormattableBody extends FormattableBody implements DatabindAware {
    private final DatabindContext databind;

    protected DatabindFormattableBody(final FormatParameters format, final DatabindContext databind) {
        super(format);
        this.databind = requireNonNull(databind);
    }

    @Override
    public final DatabindContext databind() {
        return databind;
    }

    @Override
    protected final void formatToJSON(final OutputStream out, final FormatParameters format) throws IOException {
        formatToJSON(out, format, databind());
    }

    protected abstract void formatToJSON(OutputStream out, FormatParameters format, DatabindContext databind)
        throws IOException;

    @Override
    protected final void formatToXML(final OutputStream out, final FormatParameters format) throws IOException {
        formatToXML(out, format, databind());
    }

    protected abstract void formatToXML(OutputStream out, FormatParameters format, DatabindContext databind)
        throws IOException;
}
