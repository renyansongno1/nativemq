package org.cloud.mq.extension.nativemq.deployment;

import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeReinitializedClassBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageRunnerBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.cloud.mq.extension.nativemq.runtime.RocksdbRecover;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.util.Environment;

import java.io.IOException;

/**
 * Rocksdb Extension
 * @author renyansong
 */
public class RocksdbExtensionProcessor {

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void build(BuildProducer<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses,
               BuildProducer<RuntimeReinitializedClassBuildItem> reinitialized,
               BuildProducer<NativeImageResourceBuildItem> nativeLibs,
               NativeImageRunnerBuildItem nativeImageRunner) throws IOException {

        registerClassesThatAreAccessedViaJni(jniRuntimeAccessibleClasses);
        addSupportForRocksDbLib(nativeLibs, nativeImageRunner);
        enableLoadOfNativeLibs(reinitialized);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void loadRocksDb(RocksdbRecover rocksdbRecover) {
        // Explicitly loading RocksDB native libs, as that's normally done from within
        // static initializers which already ran during build
        rocksdbRecover.loadRocksDb();
    }

    private void registerClassesThatAreAccessedViaJni(BuildProducer<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses) {
        jniRuntimeAccessibleClasses
                .produce(new JniRuntimeAccessBuildItem(true, false, false, RocksDBException.class, Status.class, RocksDB.Version.class));
    }

    private void addSupportForRocksDbLib(BuildProducer<NativeImageResourceBuildItem> nativeLibs,
                                         NativeImageRunnerBuildItem nativeImageRunnerFactory) {
        // for RocksDB, either add linux64 native lib when targeting containers
        if (nativeImageRunnerFactory.isContainerBuild()) {
            nativeLibs.produce(new NativeImageResourceBuildItem("librocksdbjni-linux-aarch64.so"));
        }
        // otherwise the native lib of the platform this build runs on
        else {
            nativeLibs.produce(new NativeImageResourceBuildItem(Environment.getJniLibraryFileName("rocksdb")));
        }
    }

    private void enableLoadOfNativeLibs(BuildProducer<RuntimeReinitializedClassBuildItem> reinitialized) {
        reinitialized.produce(new RuntimeReinitializedClassBuildItem("org.rocksdb.RocksDB"));
    }

}
