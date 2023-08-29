package org.cloud.mq.meta.server.interceptor;

import com.google.gson.Gson;
import io.grpc.*;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@GlobalInterceptor
@Slf4j
public class GrpcServerInterceptor implements ServerInterceptor {

    private static final Gson GSON = new Gson();

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        log.info("Received request header: {}", GSON.toJson(headers));
        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onMessage(ReqT message) {
                log.info("Received request message: {}", GSON.toJson(message));
                super.onMessage(message);
            }

            @Override
            public void onComplete() {
                log.info("Response complete");
                super.onComplete();
            }
        };
    }
}
