package org.cloud.mq.extension.nativemq.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class NativemqExtensionProcessor {

    @BuildStep
    void reflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                ReflectiveClassBuildItem.builder("com.sun.jndi.dns.DnsContextFactory").build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder("com.sun.jndi.rmi.registry.RegistryContextFactory")
                .build());
    }
}
