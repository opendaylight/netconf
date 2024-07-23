/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A bridge capturing a {@link FormattableBody} and {@link PrettyPrintParam}.
 */
@NonNullByDefault
record JaxRsFormattableBody(FormattableBody body, PrettyPrintParam prettyPrint) {
    JaxRsFormattableBody {
        requireNonNull(body);
        requireNonNull(prettyPrint);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("body", body).add("prettyPrint", prettyPrint.value()).toString();
    }
}
