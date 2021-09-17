/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils;

import com.google.common.base.Splitter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.odlext.model.api.OpenDaylightExtensionsConstants;
import org.opendaylight.yangtools.odlext.model.api.OpenDaylightExtensionsStatements;

/**
 * Util class for Restconf constants.
 */
@NonNullByDefault
public final class RestconfConstants {
    public static final Splitter SLASH_SPLITTER = Splitter.on('/');
    public static final String BASE_URI_PATTERN = "rests";
    public static final String NOTIF = "notif";


    public static final String MOUNT_MODULE = OpenDaylightExtensionsConstants.ORIGINAL_SOURCE.getName();
    public static final String MOUNT_IDENTIFIER =
        OpenDaylightExtensionsStatements.MOUNT.getStatementName().getLocalName();

    // FIXME: Remove this constant. All logic relying on this constant should instead rely on YangInstanceIdentifier
    //        equivalent coming out of argument parsing. This may require keeping List<YangInstanceIdentifier> as the
    //        nested path split on yang-ext:mount. This splitting needs to be based on consulting the
    //        EffectiveModelContext and allowing it only where yang-ext:mount is actually used in models.
    @Deprecated
    public static final String MOUNT = MOUNT_MODULE + ":" + MOUNT_IDENTIFIER;

    private RestconfConstants() {
        // Hidden on purpose
    }
}