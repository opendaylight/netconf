package org.opendaylight.netconf.transport.http;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(
    service = Restconf8040ConfigProvider.class,
    immediate = true,
    configurationPid = "restconf8040"
)
@Designate(ocd = Restconf8040ConfigProvider.Restconf8040Configuration.class)
public class Restconf8040ConfigProvider {

    @ObjectClassDefinition(name = "Restconf 8040 Configuration")
    public @interface Restconf8040Configuration {
        @AttributeDefinition(description = "Chunk size for RESTCONF responses")
        int chunk$_$size() default 262144;
    }

    private int chunkSize;

    @Activate
    @Modified
    public void activate(Restconf8040Configuration config) {
        this.chunkSize = config.chunk$_$size();
    }

    public int getChunkSize() {
        return chunkSize;
    }
}