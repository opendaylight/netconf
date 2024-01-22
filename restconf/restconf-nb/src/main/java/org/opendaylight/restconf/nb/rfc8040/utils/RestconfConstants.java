/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils;

import com.google.common.base.Splitter;

/**
 * Util class for Restconf constants.
 */
public final class RestconfConstants {
    public static final Splitter SLASH_SPLITTER = Splitter.on('/');
    public static final String NOTIF = "notif";

    // FIXME: Remove this constant. All logic relying on this constant should instead rely on YangInstanceIdentifier
    //        equivalent coming out of argument parsing. This may require keeping List<YangInstanceIdentifier> as the
    //        nested path split on yang-ext:mount. This splitting needs to be based on consulting the
    //        EffectiveModelContext and allowing it only where yang-ext:mount is actually used in models.
    public static final String MOUNT = "yang-ext:mount";

    private RestconfConstants() {
        // Hidden on purpose
    }
}