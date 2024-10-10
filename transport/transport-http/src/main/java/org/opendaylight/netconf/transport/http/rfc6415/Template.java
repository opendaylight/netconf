/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import static java.util.Objects.requireNonNull;

import java.net.URI;

// FIXME: https://www.rfc-editor.org/rfc/rfc6415.html#section-3.1.1:
//
//              However, a "Link" element with a "template" ...
public record Template(URI rel, String template) implements Link {
    public Template {
        requireNonNull(rel);
        requireNonNull(template);
    }
}