/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api.query;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;

/**
 * This class represents a {@code start-time} parameter as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.7">RFC8040 section 4.8.7</a>.
 */
public final class StartTimeParam extends AbstractReplayParam<StartTimeParam> {
    // API consistency: must not be confused with enum constants
    @SuppressWarnings("checkstyle:ConstantName")
    public static final @NonNull String uriName = "start-time";

    private StartTimeParam(final DateAndTime value) {
        super(value);
    }

    public static @NonNull StartTimeParam of(final DateAndTime value) {
        return new StartTimeParam(value);
    }

    @Override
    public Class<StartTimeParam> javaClass() {
        return StartTimeParam.class;
    }

    @Override
    public String paramName() {
        return uriName;
    }

    public static @NonNull StartTimeParam forUriValue(final String uriValue) {
        return of(new DateAndTime(uriValue));
    }
}
