/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.RpcError;

public class RestconfErrorXmlParserTest {

    RestconfErrorXmlParser parser = new RestconfErrorXmlParser();

    @Test
    public void testParseError() throws Exception {
        final String errorMessage = StringUtils.join(Files.readAllLines(Paths.get(getClass().getResource("/xml/errors.xml").toURI()), Charset.defaultCharset()), "");
        final Collection<RpcError> rpcErrors = parser.parseErrors(errorMessage);
        Assert.assertEquals(1, rpcErrors.size());
        final RpcError error = rpcErrors.iterator().next();
        Assert.assertEquals(RpcError.ErrorType.APPLICATION, error.getErrorType());
        Assert.assertEquals("operation-not-supported", error.getTag());
        Assert.assertEquals("No implementation of RPC available", error.getMessage());
    }
}
