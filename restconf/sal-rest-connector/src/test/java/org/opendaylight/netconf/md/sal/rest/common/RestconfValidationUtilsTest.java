/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.md.sal.rest.common;

import java.lang.reflect.Constructor;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;

public class RestconfValidationUtilsTest {

    @SuppressWarnings("unchecked")
    @Test(expected = UnsupportedOperationException.class)
    public void constructorTest() throws Throwable {
        final Constructor<RestconfValidationUtils> constructor =
                (Constructor<RestconfValidationUtils>) RestconfValidationUtils.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        final Object[] objs = {};
        try {
            constructor.newInstance(objs);
        } catch (final Exception e) {
            throw e.getCause();
        }
    }

    @Test(expected = RestconfDocumentedException.class)
    public void checkDocumentedErrorTest() {
        RestconfValidationUtils.checkDocumentedError(true, ErrorType.PROTOCOL, ErrorTag.ACCESS_DENIED, "msg");
        RestconfValidationUtils.checkDocumentedError(false, ErrorType.PROTOCOL, ErrorTag.ACCESS_DENIED, "msg");
    }

    @Test(expected = RestconfDocumentedException.class)
    public void checkNotNullDocumentedTest() {
        Object value = new Object();
        final Object result = RestconfValidationUtils.checkNotNullDocumented(value, "moduleName");
        Assert.assertEquals(value, result);

        value = null;
        RestconfValidationUtils.checkNotNullDocumented(value, "moduleName");
    }

}
