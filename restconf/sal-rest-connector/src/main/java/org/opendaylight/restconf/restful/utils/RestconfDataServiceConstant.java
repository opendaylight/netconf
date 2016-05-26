/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

/**
 * Constants for RestconfDataService
 *
 */
public final class RestconfDataServiceConstant {

    public static final String CONTENT = "content";

    private RestconfDataServiceConstant() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Constants for read data
     *
     */
    public final class ReadData {

        public static final String CONFIG = "config";
        public static final String NONCONFIG = "nonconfig";
        public static final String ALL = "all";
        public static final String READ_TYPE_TX = "READ";

        private ReadData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }

}
