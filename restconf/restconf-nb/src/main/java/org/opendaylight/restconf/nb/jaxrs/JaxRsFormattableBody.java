/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.FormattableBody;

/**
 * A bridge capturing a {@link FormattableBody} and {@link FormatParameters}./
 */
@NonNullByDefault
record JaxRsFormattableBody(FormattableBody body, FormatParameters format) {
    JaxRsFormattableBody {
        requireNonNull(body);
        requireNonNull(format);
    }
}
