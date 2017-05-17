/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import static org.opendaylight.netconf.mdsal.connector.ops.Datastore.candidate;
import static org.opendaylight.netconf.mdsal.connector.ops.Datastore.running;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import java.util.List;
import org.opendaylight.controller.config.facade.xml.TestOption;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.file.NetconfFileService;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class CopyConfig extends YangValidationNetconfOperation {
    private static final String OPERATION_NAME = "copy-config";
    private final TransactionProvider transactionProvider;
    private final List<CopyConfigSubOperation> subOperationList;

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

    public CopyConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext, final TransactionProvider transactionProvider, final NetconfFileService netconfFileService) {
        super(netconfSessionIdForReporting, schemaContext, netconfFileService);
        this.transactionProvider = transactionProvider;

        subOperationList = registerSubOperations();
    }

    private List<CopyConfigSubOperation> registerSubOperations() {
        return Lists.newArrayList(
                new CandidateToRunning(),
                new RunningToCandidate(),
                new UrlToCandidate(),
                new UrlToRunning()
//                new CandidateToUrl(),
//                new RunningToUrl()
        );
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) throws DocumentedException {
        final MdsalNetconfParameter source =  extractSourceParameter(operationElement);
        final MdsalNetconfParameter target =  extractTargetParameter(operationElement);

        for (CopyConfigSubOperation subOperation : subOperationList) {
            if (subOperation.canProcess(source,target)) {
                return subOperation.execute(document, source, target);
            }
        }
        throw new DocumentedException("Operation is not supported", DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_not_supported, DocumentedException.ErrorSeverity.error);
    }

    private class UrlToCandidate extends CopyConfigSubOperation {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return source.getFile() != null && candidate == target.getDatastore();
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            XmlElement configElement = readConfigElementFromFile(source.getFile());

            EditConfig editConfig = new EditConfig(getNetconfSessionIdForReporting(), getSchemaContext(), transactionProvider, getNetconfFileService());
            editConfig.executeEditConfig(candidate, ModifyAction.CREATE, TestOption.testThenSet, configElement);
            return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
        }
    }

    private class UrlToRunning extends CopyConfigSubOperation {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return source.getFile() != null && running == target.getDatastore();
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            XmlElement configElement = readConfigElementFromFile(source.getFile());

            EditConfig editConfig = new EditConfig(getNetconfSessionIdForReporting(), getSchemaContext(), transactionProvider, getNetconfFileService());
            editConfig.executeEditConfig(running, ModifyAction.CREATE, TestOption.testThenSet, configElement);
            return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
        }
    }

    private class CandidateToUrl extends CopyConfigSubOperation {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return candidate == source.getDatastore() && target.getFile() != null;
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            XmlElement configElement = readConfigElementFromFile(source.getFile());

            EditConfig editConfig = new EditConfig(getNetconfSessionIdForReporting(), getSchemaContext(), transactionProvider, getNetconfFileService());
            editConfig.executeEditConfig(running, ModifyAction.CREATE, TestOption.testThenSet, configElement);
            return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
        }
    }

    private class RunningToUrl extends CopyConfigSubOperation {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return running == source.getDatastore() && target.getFile() != null;
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            XmlElement configElement = readConfigElementFromFile(source.getFile());

            EditConfig editConfig = new EditConfig(getNetconfSessionIdForReporting(), getSchemaContext(), transactionProvider, getNetconfFileService());
            editConfig.executeEditConfig(running, ModifyAction.CREATE, TestOption.testThenSet, configElement);
            return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
        }
    }

    private class CandidateToRunning extends CopyConfigSubOperation {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return candidate == source.getDatastore() && running == target.getDatastore();
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            return new Commit(getNetconfSessionIdForReporting(), transactionProvider).handleWithNoSubsequentOperations(document,null);
        }
    }

    private class RunningToCandidate extends CopyConfigSubOperation {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return running == source.getDatastore() && candidate == target.getDatastore();
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            return new DiscardChanges(getNetconfSessionIdForReporting(), transactionProvider).handleWithNoSubsequentOperations(document,null);
        }
    }
}

abstract class CopyConfigSubOperation {
    abstract boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target);
    abstract Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException;
}

