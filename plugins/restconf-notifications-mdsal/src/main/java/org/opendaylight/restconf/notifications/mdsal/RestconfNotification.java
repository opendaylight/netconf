package org.opendaylight.restconf.notifications.mdsal;

import java.time.Instant;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public class RestconfNotification implements DOMNotification {
    private final ContainerNode content;

    RestconfNotification(final ContainerNode content) {
        this.content = content;
        }
    @Override
    public SchemaNodeIdentifier.@NonNull Absolute getType() {
        return Absolute.of(content.name().getNodeType());
    }

    @Override
    public @NonNull ContainerNode getBody() {
        return content;
    }
}
