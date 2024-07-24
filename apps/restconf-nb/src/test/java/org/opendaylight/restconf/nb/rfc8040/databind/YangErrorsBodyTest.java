/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ErrorMessage;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerErrorInfo;
import org.opendaylight.restconf.server.api.ServerErrorPath;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.restconf.server.spi.AbstractJukeboxTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class YangErrorsBodyTest extends AbstractJukeboxTest {
    private static final QNameModule MONITORING_MODULE_INFO =
        QNameModule.ofRevision("instance:identifier:patch:module", "2015-11-21");
    private static final DatabindContext DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResources(YangErrorsBodyTest.class,
            "/restconf/impl/ietf-restconf@2017-01-26.yang",
            "/instance-identifier/instance-identifier-patch-module.yang"));

    @Test
    void sampleComplexError() {
        final var body = new YangErrorsBody(List.of(
            new ServerError(ErrorType.APPLICATION, ErrorTag.BAD_ATTRIBUTE, new ErrorMessage("message 1"), "app tag #1",
                null, null),
            new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, new ErrorMessage("message 2"),
                "app tag #2", null, new ServerErrorInfo("my info")),
            new ServerError(ErrorType.RPC, ErrorTag.DATA_MISSING, new ErrorMessage("message 3"), " app tag #3",
                new ServerErrorPath(DATABIND, YangInstanceIdentifier.builder()
                    .node(QName.create(MONITORING_MODULE_INFO, "patch-cont"))
                    .node(QName.create(MONITORING_MODULE_INFO, "my-list1"))
                    .nodeWithKey(QName.create(MONITORING_MODULE_INFO, "my-list1"),
                        QName.create(MONITORING_MODULE_INFO, "name"), "sample")
                    .node(QName.create(MONITORING_MODULE_INFO, "my-leaf12"))
                    .build()), new ServerErrorInfo("my error info"))));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "operation-failed",
                    "error-message": "Sample error message",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <errors xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">
            <error>
              <error-message>message 1</error-message>
              <error-tag>bad-attribute</error-tag>
              <error-type>application</error-type>
            </error>
            <error>
              <error-message>message 2</error-message>
              <error-tag>operation-failed</error-tag>
              <error-type>application</error-type>
            </error>
          </errors>""", body::formatToXML, true);
    }
}
