/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.mapping;

/**
 * {@link Deprecated} move to splitted module restconf-nb-rfc8040. Util class for mapping entry stream.
 *
 */
@Deprecated
public final class RestconfMappingStreamConstants {

    public static final String DESCRIPTION = "DESCRIPTION_PLACEHOLDER";
    public static final Boolean REPLAY_SUPPORT = Boolean.valueOf(true);
    public static final String REPLAY_LOG = "";
    public static final String EVENTS = "";

    private RestconfMappingStreamConstants() {
        throw new UnsupportedOperationException("Util class");
    }
}
