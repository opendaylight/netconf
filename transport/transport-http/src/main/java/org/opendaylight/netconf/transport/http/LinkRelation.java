/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Utility constants holding the contents of
 * <a href="https://www.iana.org/assignments/link-relations/link-relations.xhtml">Link Relations</a> IANA registry,
 * governed by <a href="https://www.rfc-editor.org/rfc/rfc8288">RFC8288 Web Linking</a>.
 */
@NonNullByDefault
public final class LinkRelation {
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc6415.html#section-6.3">"lrdd" Relation Type</a>.
     */
    public static final URI LRDD = URI.create("lrdd");
    /**
     * The <a href="https://www.rfc-editor.org/rfc/rfc8040#section-11.1">"restconf" Relation Type</a>.
     */
    public static final URI RESTCONF = URI.create("restconf");

    private LinkRelation() {
        // Hidden on purpose
    }
}
