/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The contents of
 * <a href="https://www.iana.org/assignments/xml-registry/xml-registry.xhtml#ns">IETF XML ns Registry</a>relevant to
 * NETCONF.
 */
@NonNullByDefault
public final class NamespaceURN {
    /**
     * The NETCONF protocol XML namespace, as defined by <a href="https://www.rfc-editor.org/rfc/rfc6241">RFC6241</a>.
     */
    public static final String BASE = "urn:ietf:params:xml:ns:netconf:base:1.0";
    /**
     * The namespace used by the
     * <a href="https://www.rfc-editor.org/rfc/rfc6243">With-defaults Capability for NETCONF</a> extension.
     */
    public static final String DEFAULT = "urn:ietf:params:xml:ns:netconf:default:1.0";
    /**
     * The namespace used by the
     * <a href="https://datatracker.ietf.org/doc/html/draft-varga-netconf-exi-capability-01">
     * Efficient XML Interchange Capability for NETCONF</a> extension. Note this is an expired IETF draft capability and
     * subject to change.
     */
    @Beta
    public static final String EXI = "urn:ietf:params:xml:ns:netconf:exi:1.0";
    /**
     * The namespace used by the <a href="https://www.rfc-editor.org/rfc/rfc5277">NETCONF Event Notifications</a>
     * extension.
     */
    public static final String NOTIFICATION = "urn:ietf:params:xml:ns:netconf:notification:1.0";
    /**
     * The namespace used by the <a href="https://www.rfc-editor.org/rfc/rfc5717">
     * Partial Lock Remote Procedure Call (RPC) for NETCONF</a> extension.
     */
    public static final String PARTIAL_LOCK = "urn:ietf:params:xml:ns:netconf:partial-lock:1.0";

    private NamespaceURN() {
        // Hidden on purpose
    }
}
