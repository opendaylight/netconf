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
import org.eclipse.jdt.annotation.NonNullByDefault;

// https://www.rfc-editor.org/rfc/rfc6415.html#section-3.1.1:
//      The XRD "Link" element, when used with the "href" attribute, conveys
//      a link relation between the host described by the document and a
//      common target URI.
//
//      For example, the following link declares a common copyright license
//      for the entire scope:
//
//        <Link rel='copyright' href='http://example.com/copyright' />
@NonNullByDefault
public record TargetUri(URI rel, URI href) implements Link {
    public TargetUri {
        requireNonNull(rel);
        requireNonNull(href);
    }
}