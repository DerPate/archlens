package com.example.api;

import io.quarkus.grpc.GrpcService;

@GrpcService
public class GreeterService {

    public String sayHello(String request) {
        return "hello " + request;
    }

    public String sayGoodbye(String request) {
        return "bye " + request;
    }
}
