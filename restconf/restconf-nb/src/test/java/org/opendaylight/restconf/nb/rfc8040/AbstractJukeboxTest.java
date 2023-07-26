/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractJukeboxTest {
    protected static EffectiveModelContext JUKEBOX_SCHEMA;

    @BeforeClass
    public static void beforeClass() {
        JUKEBOX_SCHEMA = YangParserTestUtils.parseYangResourceDirectory("/jukebox");
    }

    @AfterClass
    public static void afterClass() {
        JUKEBOX_SCHEMA = null;
    }
}
