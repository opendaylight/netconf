/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

abstract class AbstractNotificationListenerTest {
    static final QNameModule MODULE = QNameModule.create(XMLNamespace.of("notifi:mod"), Revision.of("2016-11-23"));

    static EffectiveModelContext SCHEMA_CONTEXT;

    @BeforeClass
    public static final void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/notifications");
    }

    @AfterClass
    public static final void afterClass() {
        SCHEMA_CONTEXT = null;
    }
}
