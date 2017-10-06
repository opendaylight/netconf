/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.testtool.core.tests;

import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.netconf.testtool.core.impl.SchemaSourceCache;

import java.util.regex.Matcher;

public class RexgexpTest {

    @Test
    public void testRegexp() {
        String fileName = "/modules/module-name@2017-01-02.yang";
        final Matcher matcher = SchemaSourceCache.CACHED_FILE_PATTERN.matcher(fileName);
        boolean result = matcher.matches();
        Assert.assertTrue(result);
        final String moduleName = matcher.group("moduleName");
        final String revision = matcher.group("revision");
        Assert.assertEquals("module-name", moduleName);
        Assert.assertEquals("2017-01-02", revision);
    }

}
