/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

public final class CloseableUtil {
    private CloseableUtil() {

    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void closeAll(Iterable<? extends AutoCloseable> autoCloseables) throws Exception {
        Exception lastException = null;
        for (AutoCloseable autoCloseable : autoCloseables) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                if (lastException == null) {
                    lastException = e;
                } else {
                    lastException.addSuppressed(e);
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }

    }
}
