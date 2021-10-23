/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Abstract base class for StartTimeParameter and StopTimeParameter.
 */
@Beta
@NonNullByDefault
public abstract class AbstractReplayParameter implements Immutable {
    private static final URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:replay:1.0");

    private final DateAndTime value;

    AbstractReplayParameter(final DateAndTime value) {
        this.value = requireNonNull(value);
    }

    public final DateAndTime value() {
        return value;
    }

    public final String uriValue() {
        return value.getValue();
    }

    public static final URI capabilityUri() {
        return CAPABILITY;
    }
}
