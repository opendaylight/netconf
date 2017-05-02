/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.mapping;

/**
 * Util class for constants of mapping node.
 *
 */
public final class RestconfMappingNodeConstants {

    public static final String NAME = "name";
    public static final String REVISION = "revision";
    public static final String NAMESPACE = "namespace";
    public static final String FEATURE = "feature";
    public static final String DESCRIPTION = "description";
    public static final String REPLAY_SUPPORT = "replay-support";
    public static final String REPLAY_LOG = "replay-log-creation-time";
    public static final String EVENTS = "events";

    private RestconfMappingNodeConstants() {
        throw new UnsupportedOperationException("Util class.");
    }
}
