/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An <a href="https://www.rfc-editor.org/rfc/rfc6415.html#section-3.1.1">XRD Link</a>.
 */
@NonNullByDefault
public sealed interface Link permits TargetUri, Template {
    /**
     * Return the relation type, as defined by
     * <a href="https://www.rfc-editor.org/rfc/rfc8288#section-5.3">Web Linking</a>.
     *
     * @return the relation type
     */
    URI rel();
}