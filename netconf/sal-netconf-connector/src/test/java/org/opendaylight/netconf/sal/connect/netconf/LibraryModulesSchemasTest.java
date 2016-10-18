/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.Module;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

public class LibraryModulesSchemasTest {

    private static final QName nameQName = QName.create(Module.QNAME, "name");
    private static final QName revisionQName = QName.create(Module.QNAME, "revision");
    private static final String NAMESPACE_1 = "namespace";
    private static final String NAMESPACE_2 = "another-module-with-revision:namespace";
    private static final String NAMESPACE_3 = "module-without-revision:namespace";
    private static final String NAME_1 = "module-with-revision";
    private static final String NAME_2 = "another-module-with-revision";
    private static final String NAME_3 = "module-without-revision";
    private static final String REV_1 = "2014-04-08";
    private static final String REV_2 = "2013-10-21";
    private static final String REV_3 = "";
    private static final String URL_1 = "http://localhost:8181/yanglib/schemas/module-with-revision/2014-04-08";
    private static final String URL_2 = "http://localhost:8181/yanglib/schemas/another-module-with-revision/2013-10-21";
    private static final String URL_3 = "http://localhost:8181/yanglib/schemas/module-without-revision/";
    private static final RemoteDeviceId ID = new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));

    @Test
    public void testCreate() throws Exception {
        // test create from xml
        final LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.xml").toString());

        verifySchemas(libraryModulesSchemas);

        // test create from json
        final LibraryModulesSchemas libraryModuleSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.json").toString());

        verifySchemas(libraryModulesSchemas);
    }


    private void verifySchemas(final LibraryModulesSchemas libraryModulesSchemas) throws MalformedURLException {
        final Map<SourceIdentifier, URL> resolvedModulesSchema = libraryModulesSchemas.getAvailableModels();
        verifyResolvedModuleSchemas(resolvedModulesSchema);
    }

    @Test
    public void testCreateInvalidModulesEntries() throws Exception {
        final LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library-fail.xml").toString());

        final Map<SourceIdentifier, URL> resolvedModulesSchema = libraryModulesSchemas.getAvailableModels();
        Assert.assertThat(resolvedModulesSchema.size(), is(1));

        Assert.assertFalse(resolvedModulesSchema.containsKey(
                RevisionSourceIdentifier.create("module-with-bad-url")));
        Assert.assertFalse(resolvedModulesSchema.containsKey(
                RevisionSourceIdentifier.create("module-with-bad-revision", "bad-revision")));
        Assert.assertTrue(resolvedModulesSchema.containsKey(
                RevisionSourceIdentifier.create("good-ol-module")));
    }


    @Test
    public void testCreateFromInvalidAll() throws Exception {
        // test bad yang lib url
        final LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create("ObviouslyBadUrl");
        Assert.assertThat(libraryModulesSchemas.getAvailableModels(), is(Collections.emptyMap()));

        // TODO test also fail on json and xml parsing. But can we fail not on runtime exceptions?
    }

    @Test
    public void testCreate2() throws Exception {
        final DOMRpcService rpc = mock(DOMRpcService.class);

        final MapEntryNode module1 = createModuleEntry(NAMESPACE_1, NAME_1, REV_1,
                URL_1);
        final MapEntryNode module2 = createModuleEntry(NAMESPACE_2, NAME_2, REV_2,
                URL_2);
        final MapEntryNode module3 = createModuleEntry(NAMESPACE_3, NAME_3, REV_3,
                URL_3);
        final MapNode moduleList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Module.QNAME))
                .withChild(module1)
                .withChild(module2)
                .withChild(module3)
                .build();
        final ContainerNode modulesState = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(ModulesState.QNAME))
                .withChild(moduleList)
                .build();
        final ContainerNode data = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_QNAME))
                .withChild(modulesState)
                .build();
        final ContainerNode rpcReply = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME))
                .withChild(data)
                .build();

        final DOMRpcResult result = new DefaultDOMRpcResult(rpcReply);
        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFuture = Futures.immediateCheckedFuture(result);
        when(rpc.invokeRpc(any(), any())).thenReturn(resultFuture);
        final LibraryModulesSchemas schemas = LibraryModulesSchemas.create(rpc, ID);
        final Map resolvedModulesSchema = schemas.getAvailableModels();
        verifyResolvedModuleSchemas(resolvedModulesSchema);
    }

    @Test
    public void testCreateRpcError() throws Exception {
        final DOMRpcService rpc = mock(DOMRpcService.class);
        final DOMRpcResult result = new DefaultDOMRpcResult(RpcResultBuilder.newError(RpcError.ErrorType.TRANSPORT, "error", "error"));
        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFuture = Futures.immediateCheckedFuture(result);
        when(rpc.invokeRpc(any(), any())).thenReturn(resultFuture);
        final LibraryModulesSchemas schemas = LibraryModulesSchemas.create(rpc, ID);
        Assert.assertTrue(schemas.getAvailableModels().isEmpty());
    }

    @Test
    public void testCreateEmptyRpcResult() throws Exception {
        final DOMRpcService rpc = mock(DOMRpcService.class);
        final DOMRpcResult result = new DefaultDOMRpcResult();
        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFuture = Futures.immediateCheckedFuture(result);
        when(rpc.invokeRpc(any(), any())).thenReturn(resultFuture);
        final LibraryModulesSchemas schemas = LibraryModulesSchemas.create(rpc, ID);
        Assert.assertTrue(schemas.getAvailableModels().isEmpty());
    }

    @Test
    public void testCreateException() throws Exception {
        final DOMRpcService rpc = mock(DOMRpcService.class);
        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFuture = Futures.immediateFailedCheckedFuture(
                new DOMRpcImplementationNotAvailableException("not available"));
        when(rpc.invokeRpc(any(), any())).thenReturn(resultFuture);
        final LibraryModulesSchemas schemas = LibraryModulesSchemas.create(rpc, ID);
        Assert.assertTrue(schemas.getAvailableModels().isEmpty());
    }

    @Test
    public void testGetAvailableYangSchemasQNames() throws Exception {
        final String url = getClass().getResource("/yang-library-fail.xml").toString();
        final LibraryModulesSchemas schemas = LibraryModulesSchemas.create(url);
        Assert.assertNull(schemas.getAvailableYangSchemasQNames());
    }

    private static void verifyResolvedModuleSchemas(final Map resolvedModulesSchema) throws MalformedURLException {
        Assert.assertThat(resolvedModulesSchema.size(), is(3));

        Assert.assertTrue(resolvedModulesSchema.containsKey(RevisionSourceIdentifier.create(NAME_1,
                REV_1)));
        Assert.assertThat(resolvedModulesSchema.get(
                RevisionSourceIdentifier.create(NAME_1, REV_1)),
                is(new URL(URL_1)));

        Assert.assertTrue(resolvedModulesSchema.containsKey(
                RevisionSourceIdentifier.create(NAME_2, REV_2)));
        Assert.assertThat(resolvedModulesSchema.get(
                RevisionSourceIdentifier.create(NAME_2, REV_2)),
                is(new URL(URL_2)));

        Assert.assertTrue(resolvedModulesSchema.containsKey(
                RevisionSourceIdentifier.create(NAME_3)));
        Assert.assertThat(resolvedModulesSchema.get(
                RevisionSourceIdentifier.create(NAME_3)),
                is(new URL(URL_3)));
    }

    private static MapEntryNode createModuleEntry(final String namespaceVal, final String nameVal, final String revVal, final String urlVal) {
        final Map<QName, Object> keys1 = new HashMap<>();
        keys1.put(nameQName, nameVal);
        keys1.put(revisionQName, revVal);
        final LeafNode name = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "name")))
                .withValue(nameVal)
                .build();
        final LeafNode revision = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "revision")))
                .withValue(revVal)
                .build();
        final LeafNode namespace = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "namespace")))
                .withValue(namespaceVal)
                .build();
        final LeafNode schemaLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "schema")))
                .withValue(urlVal)
                .build();
        return Builders.mapEntryBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifierWithPredicates(Module.QNAME, keys1))
                .withChild(name)
                .withChild(revision)
                .withChild(namespace)
                .withChild(schemaLeaf)
                .build();
    }
}