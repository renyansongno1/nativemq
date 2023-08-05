package org.cloud.mq.extension.nativemq.runtime;

import io.quarkus.runtime.annotations.Recorder;
import org.rocksdb.RocksDB;

@Recorder
public class RocksdbRecover {
    public void loadRocksDb() {
        RocksDB.loadLibrary();
    }
}
