/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;

/**
 * Abstract base class for StartTimeParameter and StopTimeParameter.
 */
public abstract sealed class AbstractReplayParam<T extends AbstractReplayParam<T>> implements RestconfQueryParam<T>
        permits StartTimeParam, StopTimeParam {
    private static final @NonNull URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:replay:1.0");

    private final @NonNull DateAndTime value;

    AbstractReplayParam(final DateAndTime value) {
        this.value = requireNonNull(value);
    }

    public final @NonNull DateAndTime value() {
        return value;
    }

    @Override
    public final String paramValue() {
        return value.getValue();
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).add("value", paramValue()).toString();
    }

    public static final @NonNull URI capabilityUri() {
        return CAPABILITY;
    }
}
