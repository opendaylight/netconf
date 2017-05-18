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
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfigInput.ErrorOption.StopOnError;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import java.util.List;
import org.opendaylight.controller.config.facade.xml.TestOption;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlMappingConstants;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.file.NetconfFileService;
import org.opendaylight.netconf.mdsal.connector.ops.get.GetConfig;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class CopyConfig extends ValidateNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(CopyConfig.class);

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
                new UrlToRunning(),
                new CandidateToUrl(),
                new RunningToUrl()
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

        protected Element executeByDatastore(Document document, MdsalNetconfParameter source, Datastore datastore) throws DocumentedException {
            XmlElement configElement = readConfigElementFromFile(source.getFile());

            EditConfig editConfig = new EditConfig(getNetconfSessionIdForReporting(), getSchemaContext(), transactionProvider, getNetconfFileService());
            editConfig.executeEditConfig(datastore, ModifyAction.CREATE, TestOption.testThenSet, configElement, StopOnError);
            return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            return executeByDatastore(document,source,candidate);
        }
    }

    private class UrlToRunning extends UrlToCandidate {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return source.getFile() != null && running == target.getDatastore();
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            return executeByDatastore(document,source,running);
        }
    }

    private class CandidateToUrl extends CopyConfigSubOperation {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return candidate == source.getDatastore() && target.getFile() != null;
        }

        private void copyChildsBetweenElements(Element from, Element to) {
            if(XmlElement.fromDomElement(from).hasNamespace()) {
                to.appendChild(from);
            } else {
                NodeList list = from.getChildNodes();
                while(list.getLength()!=0) {
                    to.appendChild(list.item(0));
                }
            }
        }

        private Document retrieveDataToDocument(Datastore datastore) throws DocumentedException {
            Document newDocument = XmlUtil.newDocument();
            GetConfig getConfig = new GetConfig(getNetconfSessionIdForReporting(), getSchemaContext(), transactionProvider);
            Element response =  getConfig.readData(newDocument,datastore,YangInstanceIdentifier.EMPTY);
            Element reply = XmlUtil.createElement(newDocument, XmlMappingConstants.CONFIG_KEY, Optional.absent());

            copyChildsBetweenElements(response, reply);
            newDocument.appendChild(reply);

            return newDocument;
        }

        protected Element executeByDatastore(Document document, MdsalNetconfParameter target, Datastore datastore) throws DocumentedException {
            Document newDocument = retrieveDataToDocument(datastore);

            try {
                getNetconfFileService().writeContentToFile(target.getFile(),newDocument);
            } catch (Exception e) {
                LOG.error(e.getMessage(),e);
                throw new DocumentedException("Cannot store data to "+target.getFile().getAbsoluteFile(), DocumentedException.ErrorType.application, DocumentedException.ErrorTag.operation_not_supported, DocumentedException.ErrorSeverity.error);
            }

            return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
        }
        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            return executeByDatastore(document, target, candidate);
        }
    }

    private class RunningToUrl extends CandidateToUrl {
        @Override
        boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target) {
            return running == source.getDatastore() && target.getFile() != null;
        }

        @Override
        Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException {
            return executeByDatastore(document, target, running);
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

    private abstract class CopyConfigSubOperation {
        abstract boolean canProcess(MdsalNetconfParameter source, MdsalNetconfParameter target);
        abstract Element execute(Document document, MdsalNetconfParameter source, MdsalNetconfParameter target) throws DocumentedException;
    }
}



