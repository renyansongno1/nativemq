package org.cloud.mq.meta.server.interceptor;

import com.google.gson.Gson;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcClientInterceptor implements ClientInterceptor {

    private static final Gson GSON = new Gson();

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                log.info("Sending request header: {}", GSON.toJson(headers));

                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onMessage(RespT message) {
                        log.info("Received response message: {}", GSON.toJson(message));

                        super.onMessage(message);
                    }
                }, headers);
            }

            @Override
            public void halfClose() {
                log.info("Request complete");
                super.halfClose();
            }
        };
    }
}
