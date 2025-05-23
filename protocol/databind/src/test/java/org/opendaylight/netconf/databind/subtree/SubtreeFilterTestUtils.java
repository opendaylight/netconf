/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import org.opendaylight.yangtools.yang.common.QName;

final class SubtreeFilterTestUtils {
    private SubtreeFilterTestUtils() {
        // Hidden on purpose
    }

    static final String NAMESPACE = "http://example.com/schema/1.2/config";
    static final String NAMESPACE2 = "http://example.com/schema/1.2/config2";
    static final QName TYPE_QNAME = QName.create(NAMESPACE, "type").intern();
    static final QName TOP_QNAME = QName.create(NAMESPACE, "top").intern();
    static final QName INTERFACES_QNAME = QName.create(NAMESPACE, "interfaces").intern();
    static final QName INTERFACE_QNAME = QName.create(NAMESPACE, "interface").intern();
    static final QName IFNAME_QNAME = QName.create(NAMESPACE, "ifName").intern();
    static final QName USERS_QNAME = QName.create(NAMESPACE, "users").intern();
    static final QName USER_QNAME = QName.create(NAMESPACE, "user").intern();
    static final QName NAME_QNAME = QName.create(NAMESPACE, "name").intern();
    static final QName COMPANY_INFO_QNAME = QName.create(NAMESPACE, "company-info").intern();
    static final String REVISION = "2025-03-31";
    static final String REVISION2 = "2025-04-01";
}
