/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.InputStream;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.XmlOperationInputBody;

public class XmlOperationInputBodyTest extends AbstractOperationInputBodyTest {
    @Override
    OperationInputBody moduleSubContainerDataPostActionBody() {
        return new XmlOperationInputBody(stringInputStream("""
            <input xmlns="instance:identifier:module">
              <delay>600</delay>
            </input>"""));
    }

    @Override
    OperationInputBody testEmptyBody() {
        return new XmlOperationInputBody(InputStream.nullInputStream());
    }

    @Override
    OperationInputBody testRpcModuleInputBody() {
        return new XmlOperationInputBody(stringInputStream("""
            <input xmlns="invoke:rpc:module">
              <cont>
                <lf>lf-test</lf>
              </cont>
            </input>"""));
    }
}
