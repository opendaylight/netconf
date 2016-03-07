/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils;

public class RestconfConstants {

    public static final String XML = "+xml";
    public static final String JSON = "+json";
    public static final String YANG_MEDIA_TYPE = "application/yang";
    public static final String YIN_MEDIA_TYPE = "application/yin+xml";

    public static String IDENTIFIER = "identifier";

    private RestconfConstants() {
        throw new UnsupportedOperationException("Util class");
    }
}