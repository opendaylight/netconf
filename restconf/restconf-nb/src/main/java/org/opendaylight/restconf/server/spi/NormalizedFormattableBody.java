/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindFormattableBody;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A {@link FormattableBody} representing a data resource.
 */
@NonNullByDefault
public final class NormalizedFormattableBody<N extends NormalizedNode> extends DatabindFormattableBody {
    private final Inference inference;
    private final N data;

    public NormalizedFormattableBody(final FormatParameters format, final DatabindContext databind,
            final Inference inference, final N data) {
        super(format, databind);
        this.inference = requireNonNull(inference);
        this.data = requireNonNull(data);
    }

    @Override
    protected void formatToJSON(final OutputStream out, final FormatParameters format, final DatabindContext databind)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void formatToXML(final OutputStream out, final FormatParameters format, final DatabindContext databind)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("body", data.prettyTree()));
    }
}
