/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.InputStream;
import org.opendaylight.restconf.server.api.JsonOperationInputBody;
import org.opendaylight.restconf.server.api.OperationInputBody;

public class JsonOperationInputBodyTest extends AbstractOperationInputBodyTest {
    @Override
    OperationInputBody moduleSubContainerDataPostActionBody() {
        return new JsonOperationInputBody(stringInputStream("""
            {
              "instance-identifier-module:input": {
                "delay": 600
              }
            }"""));
    }

    @Override
    OperationInputBody testEmptyBody() {
        return new JsonOperationInputBody(InputStream.nullInputStream());
    }

    @Override
    OperationInputBody testRpcModuleInputBody() {
        return new JsonOperationInputBody(stringInputStream("""
            {
              "invoke-rpc-module:input" : {
                "cont" : {
                  "lf" : "lf-test"
                }
              }
            }"""));
    }
}
