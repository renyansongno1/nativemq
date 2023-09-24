package org.cloud.mq.meta.server.raft.log;

import com.google.common.primitives.Longs;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.log.exception.LogProxyException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.rocksdb.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * raft log proxy
 * @author renyansong
 */
@Startup
@ApplicationScoped
@Slf4j
public class LogProxy {

    @ConfigProperty(name = "QUARKUS_KUBERNETES_MOUNTS__METADATA-LOG-PVC__PATH")
    String logMountPath;

    private RocksDB rocksDb;

    @PostConstruct
    public void init() throws Exception{
        logMountPath += RaftUtils.getIdByHost(null);
        log.info("rocksdb log path:{}", logMountPath);
        File file = new File(logMountPath);
        if (!file.exists()) {
            boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                log.error("mkdir log path for:{} fail", logMountPath);
                return;
            }
        }
        Options options = new Options();
        options.setCreateIfMissing(true);
        options.setWalRecoveryMode(WALRecoveryMode.AbsoluteConsistency);
        options.setWalTtlSeconds(0);
        rocksDb = RocksDB.open(options, logMountPath);
    }

    @PreDestroy
    public void destroy() {
        if (rocksDb != null) {
            rocksDb.close();
        }
    }

    /**
     * append log
     * @param data log data for value
     * @param logIndex log index for key
     */
    public void appendLog(long logIndex, byte[] data) {
        try {
            rocksDb.put(Longs.toByteArray(logIndex), data);
        } catch (RocksDBException e) {
            throw new LogProxyException("write error", e);
        }
    }

    /**
     * read log by index
     * @param logIndex log index
     * @return log data or null
     */
    public byte[] readIndex(long logIndex) {
        try {
            return rocksDb.get(Longs.toByteArray(logIndex));
        } catch (RocksDBException e) {
            throw new LogProxyException("read error", e);
        }
    }

    /**
     * just for test, never invoke by service
     */
    public void clearAll() throws Exception{
        RocksIterator iter = rocksDb.newIterator();
        for (iter.seekToFirst(); iter.isValid(); iter.next()) {
            rocksDb.delete(iter.key());
        }

        Path directory = Path.of(logMountPath);
        // remove all file
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); // 删除文件
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * find the last key
     * @return last key long value
     */
    public long getLastKey() {
        RocksIterator iter = rocksDb.newIterator();
        iter.seekToLast();
        if (iter.isValid()) {
            if (iter.key() == null) {
                return 0;
            }
            return Longs.fromByteArray(iter.key());
        }
        return 0;
    }

    /**
     * delete
     * @param logIndex log index
     */
    public void deleteByIndex(long logIndex) {
        try {
            rocksDb.delete(Longs.toByteArray(logIndex));
        } catch (RocksDBException e) {
            throw new LogProxyException("delete key: " + logIndex + ", error", e);
        }
    }
}
