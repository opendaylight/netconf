/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;

/**
 * This class represents a {@code start-time} parameter as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.7">RFC8040 section 4.8.7</a>.
 */
@NonNullByDefault
public final class StartTimeParameter extends AbstractReplayParameter {
    private StartTimeParameter(final DateAndTime value) {
        super(value);
    }

    public static StartTimeParameter of(final DateAndTime value) {
        return new StartTimeParameter(value);
    }

    public static String uriName() {
        return "start-time";
    }

    public static StartTimeParameter forUriValue(final String uriValue) {
        return of(new DateAndTime(uriValue));
    }
}
