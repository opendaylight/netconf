/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A well-known URI, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8615#section-3">RFC8615, section 3</a>.
 */
@Beta
@NonNullByDefault
public record WellKnownURI(String suffix) {
    /**
     * A well-known URI suffix, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8615#section-3.1">RFC8615, section 3.1</a>.
     */
    @Beta
    public interface Suffix {
        /**
         * Return the {@link WellKnownURI} corresponding to this suffix.
         *
         * @return a {@link WellKnownURI}
         */
        WellKnownURI wellKnownUri();
    }

    /**
     * The "host-meta" Well-Known URI, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6415#section-6.1">RFC6415, section 6.1</a>.
     */
    public static final WellKnownURI HOST_META = new WellKnownURI("host-meta");
    /**
     * The "host-meta.json" Well-Known URI, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc6415#section-6.2">RFC6415, section 6.2</a>.
     */
    public static final WellKnownURI HOST_META_JSON = new WellKnownURI("host-meta.json");

    public WellKnownURI {
        if (suffix.isEmpty()) {
            throw new IllegalArgumentException("empty suffix");
        }
    }
}
