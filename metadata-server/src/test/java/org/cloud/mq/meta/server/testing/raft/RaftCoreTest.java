package org.cloud.mq.meta.server.testing.raft;

import com.google.common.collect.Lists;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.RaftVoteRes;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * raft test
 * @author renyansong
 */
@QuarkusTest
@Slf4j
public class RaftCoreTest {

    private static final String PEER2 = "peer2";

    private static final String PEER3 = "peer3";

    @InjectMock
    @Inject
    RaftClient raftClient;

    @Inject
    ElectState electState;

    @BeforeEach
    void beforeRaftTest() {
        // channel mock
        List<ManagedChannel> managedChannels =  Lists.newArrayList(ManagedChannelBuilder.forAddress(PEER2, 8080)
                        .usePlaintext()
                        .build(),
                ManagedChannelBuilder.forAddress(PEER3, 8080)
                        .usePlaintext()
                        .build());
        Mockito.when(raftClient.getAllChannel()).thenReturn(managedChannels);
    }

    /**
     * vote success test
     */
    @Test
    void voteSuccessTest() {
        // Mock res
        Mockito.when(raftClient.sendVote(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Object[] arguments = invocation.getArguments();
                    if (arguments[0].toString().contains(PEER2)) {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                    } else if (arguments[0].toString().contains(PEER3)) {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.REJECT).build();
                    } else {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                    }
                });
        // send vote
        electState.becomeCandidate();
        await().atMost(Duration.ofSeconds(10)).untilAsserted( () ->
                assertThat(electState.getLeaderId()).isEqualTo(0)
        );
    }

}
