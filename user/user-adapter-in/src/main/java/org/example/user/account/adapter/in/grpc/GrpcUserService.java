package org.example.user.account.adapter.in.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.example.grpc.user.GrpcFindByEmailRequest;
import org.example.grpc.user.GrpcFindByEmailResponse;
import org.example.grpc.user.GrpcSignUpOauth2Request;
import org.example.grpc.user.GrpcSignUpOauth2Response;
import org.example.grpc.user.UserServiceGrpc;
import org.example.user.account.application.port.in.UserCommandUseCase;
import org.example.user.account.application.port.in.UserQueryUseCase;
import org.example.user.account.application.service.command.SignUpOauth2Command;
import org.example.user.account.domain.model.User;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcUserService extends UserServiceGrpc.UserServiceImplBase {

    private final UserQueryUseCase userQueryUseCase;
    private final UserCommandUseCase userCommandUseCase;
    private final GrpcUserMapper grpcUserMapper;

    @Override
    public void findByEmail(
            GrpcFindByEmailRequest request,
            StreamObserver<GrpcFindByEmailResponse> responseObserver
    ) {
        GrpcFindByEmailResponse response = userQueryUseCase.findByEmailWithRoles(request.getEmail())
                .map(grpcUserMapper::toFindByEmailResponse)
                .orElseGet(grpcUserMapper::emptyFindByEmailResponse);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void signUpOauth2(
            GrpcSignUpOauth2Request request,
            StreamObserver<GrpcSignUpOauth2Response> responseObserver
    ) {
        SignUpOauth2Command command = toSignUpOauth2Command(request);

        User newUser = userCommandUseCase.signUpOauth2(command);

        GrpcSignUpOauth2Response response = grpcUserMapper.toSignUpOauth2Response(newUser);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private SignUpOauth2Command toSignUpOauth2Command(GrpcSignUpOauth2Request request) {
        return new SignUpOauth2Command(
                request.getSub(),
                request.getEmail(),
                request.getNickname()
        );
    }
}