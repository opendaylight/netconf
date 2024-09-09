/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The contents of
 * <a href="https://www.iana.org/assignments/restconf-capability-urns/restconf-capability-urns.xhtml">
 * RESTCONF Capability URNs</a> IANA registry as well as any other capabilities we explicitly recognize.
 *
 * <p>
 * The basic concept of Capabilities is defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-9.1">RFC8040 section 9.1</a>.
 */
@NonNullByDefault
public final class CapabilityURN {
    /**
     * The "defaults" Protocol Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-9.1.2">RFC8040, section 9.1.2</a>.
     */
    public static final String DEFAULTS = "urn:ietf:params:restconf:capability:defaults:1.0";
    /**
     * Support for the "depth" Query Parameter, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.2">RFC8040, section 4.8.2</a>.
     */
    public static final String DEPTH = "urn:ietf:params:restconf:capability:depth:1.0";
    /**
     * Support for the "fields" Query Parameter, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.3">RFC8040, section 4.8.3</a>.
     */
    public static final String FIELDS = "urn:ietf:params:restconf:capability:fields:1.0";
    /**
     * Support for the "filter" Query Parameter, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.4">RFC8040, section 4.8.4</a>.
     */
    public static final String FILTER = "urn:ietf:params:restconf:capability:filter:1.0";
    /**
     * Support for notification replay using the "start-time" and "stop-time" Query Parameters, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.7">RFC8040, section 4.8.7</a> and
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.8">RFC8040, section 4.8.8</a>.
     */
    public static final String REPLAY = "urn:ietf:params:restconf:capability:replay:1.0";
    /**
     * Support for the "with-defaults" Query Parameter, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.8.9">RFC8040, section 4.8.9</a>.
     */
    public static final String WITH_DEFAULTS = "urn:ietf:params:restconf:capability:with-defaults:1.0";
    /**
     * Support for the "with-origin" Query Parameter, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8527#section-3.2.2">RFC8527, section 3.2.2</a>.
     */
    public static final String WITH_ORIGIN = "urn:ietf:params:restconf:capability:with-origin:1.0";
    /**
     * Support for the "with-defaults" Query Parameter on the Operational State Datastore, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8527#section-3.2.1">RFC8527, section 3.2.1</a>.
     */
    public static final String WITH_OPERATIONAL_DEFAULTS =
        "urn:ietf:params:restconf:capability:with-operational-defaults:1.0";
    /**
     * The ":yang-patch" RESTCONF Capability, as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8072#section-2.8">RFC8072, section 2.8.</a>.
     */
    public static final String YANG_PATCH = "urn:ietf:params:restconf:capability:yang-patch:1.0";
    /**
     * Support for the OpenDaylight-specific "changed-leaf-nodes-only" parameter.
     */
    public static final String ODL_CHANGED_LEAF_NODES_ONLY =
        "urn:opendaylight:params:restconf:capability:changed-leaf-nodes-only:1.0";
    /**
     * Support for the OpenDaylight-specific "odl-child-nodes-only" parameter.
     */
    public static final String ODL_CHILD_NODES_ONLY =
        "urn:opendaylight:params:restconf:capability:child-nodes-only:1.0";
    /**
     * Support for the OpenDaylight-specific "odl-leaf-nodes-only" parameter.
     */
    public static final String ODL_LEAF_NODES_ONLY = "urn:opendaylight:params:restconf:capability:leaf-nodes-only:1.0";
    /**
     * Support for the OpenDaylight-specific "odl-pretty-print" parameter.
     */
    public static final String ODL_PRETTY_PRINT = "urn:opendaylight:params:restconf:capability:pretty-print:1.0";
    /**
     * Support for the OpenDaylight-specific "odl-skip-notification-data" parameter.
     */
    public static final String ODL_SKIP_NOTIFICATION_DATA =
        "urn:opendaylight:params:restconf:capability:skip-notification-data:1.0";

    private CapabilityURN() {
        // Hidden on purpose
    }
}
