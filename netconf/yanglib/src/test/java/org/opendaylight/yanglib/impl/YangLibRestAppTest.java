/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.impl;

import static junit.framework.TestCase.assertTrue;

import java.util.Set;
import org.junit.Test;

public class YangLibRestAppTest {

    @Test
    public void testYangLibRestApp() {
        final YangLibRestApp yangLibRestApp = new YangLibRestApp();
        final Set singleton = yangLibRestApp.getSingletons();

        assertTrue(singleton.contains(yangLibRestApp.getYangLibService()));
    }
}
