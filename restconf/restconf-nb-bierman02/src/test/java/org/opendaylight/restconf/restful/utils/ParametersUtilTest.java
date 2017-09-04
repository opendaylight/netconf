/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;

/**
 * Unit test for {@link ParametersUtil}.
 */
public class ParametersUtilTest {

    /**
     * Test when all parameters are allowed.
     */
    @Test
    public void checkParametersTypesTest() {
        ParametersUtil.checkParametersTypes(
                RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                Sets.newHashSet("content"),
                RestconfDataServiceConstant.ReadData.CONTENT, RestconfDataServiceConstant.ReadData.DEPTH);
    }

    /**
     * Test when not allowed parameter type is used.
     */
    @Test
    public void checkParametersTypesNegativeTest() {
        try {
            ParametersUtil.checkParametersTypes(
                    RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                    Sets.newHashSet("not-allowed-parameter"),
                    RestconfDataServiceConstant.ReadData.CONTENT, RestconfDataServiceConstant.ReadData.DEPTH);

            Assert.fail("Test expected to fail due to not allowed parameter used with operation");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test when parameter is present at most once.
     */
    @Test
    public void checkParameterCountTest() {
        ParametersUtil.checkParameterCount(Lists.newArrayList("all"), RestconfDataServiceConstant.ReadData.CONTENT);
    }

    /**
     * Test when parameter is present more than once.
     */
    @Test
    public void checkParameterCountNegativeTest() {
        try {
            ParametersUtil.checkParameterCount(Lists.newArrayList("config", "nonconfig", "all"),
                    RestconfDataServiceConstant.ReadData.CONTENT);

            Assert.fail("Test expected to fail due to multiple values of the same parameter");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }
}