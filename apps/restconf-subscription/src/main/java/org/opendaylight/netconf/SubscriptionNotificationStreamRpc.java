package org.opendaylight.netconf;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangModuleInfoImpl;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.common.QName;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Singleton
@Component(service = RpcImplementation.class)
public class SubscriptionNotificationStreamRpc  extends RpcImplementation {

    private final DOMNotificationService notificationService;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public SubscriptionNotificationStreamRpc(DOMNotificationService notificationService, RestconfStream.Registry streamRegistry) {
        super(YangModuleInfoImpl.qnameOf("create-notification-stream"));
        this.notificationService = notificationService;
        this.streamRegistry = streamRegistry;
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var identifier = new YangInstanceIdentifier.NodeIdentifier(QName.create(""));


        streamRegistry.createStream(request.transform(
            stream -> ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier()
                .withChild()
                .build()), restconfURI, new SubscriptionNotificationStream(notificationService, input.path().databind().modelContext()),
            "Subscription notification stream"
            );

    }
}
