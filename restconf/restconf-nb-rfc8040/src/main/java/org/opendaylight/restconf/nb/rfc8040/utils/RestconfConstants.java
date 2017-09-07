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
 *
 */
public final class RestconfConstants {

    public static final String XML = "+xml";
    public static final String JSON = "+json";
    public static final String MOUNT = "yang-ext:mount";
    public static final String IDENTIFIER = "identifier";
    public static final char SLASH = '/';
    public static final Splitter SLASH_SPLITTER = Splitter.on(SLASH);
    public static final String DRAFT_PATTERN = "restconf/18";

    public static final CharSequence DATA_SUBSCR = "data-change-event-subscription";
    public static final CharSequence NOTIFICATION_STREAM = "notification-stream";

    private RestconfConstants() {
        throw new UnsupportedOperationException("Util class");
    }
}