/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

public final class XmlNetconfConstants {
    public static final String CAPABILITY = "capability";
    public static final String CAPABILITIES = "capabilities";
    public static final String COMMIT = "commit";
    public static final String OPERATION_ATTR_KEY = "operation";
    public static final String CONFIG_KEY = "config";
    public static final String DATA_KEY = "data";
    public static final String OK = "ok";
    public static final String FILTER = "filter";
    public static final String SOURCE_KEY = "source";
    public static final String RPC_KEY = "rpc";
    public static final String NOTIFICATION_ELEMENT_NAME = "notification";
    public static final String EVENT_TIME = "eventTime";
    public static final String PREFIX = "prefix";

    public static final String MESSAGE_ID = "message-id";
    public static final String SESSION_ID = "session-id";

    public static final String GET = "get";
    public static final String GET_CONFIG = "get-config";

    public static final String RPC_REPLY_KEY = "rpc-reply";

    private XmlNetconfConstants() {
        // Hidden on purpose
    }
}
