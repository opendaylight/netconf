/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import java.net.URI;
import java.net.URISyntaxException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;

/**
 * Constants for RestconfDataService
 *
 */
public final class RestconfDataServiceConstant {

    public static final String CONTENT = "content";
    public static final QName NETCONF_BASE_QNAME;
    static {
        try {
            NETCONF_BASE_QNAME = QName.create(
                    QNameModule.create(new URI(PutData.NETCONF_BASE), null), PutData.NETCONF_BASE_PAYLOAD_NAME);
        } catch (final URISyntaxException e) {
            final String errMsg = "It wasn't possible to create instance of URI class with " + PutData.NETCONF_BASE
                    + " URI";
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        }
    }

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

    /**
     * Constants for data to put
     *
     */
    public final class PutData {
        public static final String NETCONF_BASE = "urn:ietf:params:xml:ns:netconf:base:1.0";
        public static final String NETCONF_BASE_PAYLOAD_NAME = "data";
        public static final String PUT_TX_TYPE = "PUT";

        private PutData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }

    /**
     * Constants for data to post
     *
     */
    public final class PostData {
        public static final String POST_TX_TYPE = "POST";

        private PostData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }

    /**
     * Constants for data to delete
     *
     */
    public final class DeleteData {
        public static final String DELETE_TX_TYPE = "DELETE";

        private DeleteData() {
            throw new UnsupportedOperationException("Util class.");
        }
    }
}
