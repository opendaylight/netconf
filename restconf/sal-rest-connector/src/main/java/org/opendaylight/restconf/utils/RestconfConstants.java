/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils;

/**
 * Util class for Restconf constants.
 *
 */
public class RestconfConstants {

    public static final String XML = "+xml";
    public static final String JSON = "+json";

    public static String IDENTIFIER = "identifier";

    private RestconfConstants() {
        throw new UnsupportedOperationException("Util class");
    }
}