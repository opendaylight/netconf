/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import java.io.InputStream;

public final class TestToolUtils {
    private TestToolUtils() {

    }

    public static String getMac(long mac) {
        final StringBuilder builder = new StringBuilder(Long.toString(mac, 16));
        for (int i = builder.length(); i < 12; i++) {
            builder.insert(0, "0");
        }
        for (int j = builder.length() - 2; j >= 2; j -= 2) {
            builder.insert(j, ":");
        }
        return builder.toString();
    }

    public static InputStream getDataAsStream(final String path) {
        return TestToolUtils.class.getClassLoader().getResourceAsStream(path);
    }

}
