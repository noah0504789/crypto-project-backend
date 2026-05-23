package org.example.user.adapter.in.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.user.FindByEmailGrpcRequest;
import org.example.grpc.user.FindByEmailGrpcResponse;
import org.example.grpc.user.SignUpOauth2GrpcRequest;
import org.example.grpc.user.SignUpOauth2GrpcResponse;
import org.example.grpc.user.UserServiceGrpc;
import org.example.user.application.service.Oauth2UserSignUpService;
import org.example.user.application.service.UserQueryService;
import org.example.user.domain.model.User;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserQueryService userQueryService;
    private final Oauth2UserSignUpService oauth2UserSignUpService;
    private final UserGrpcMapper userGrpcMapper;

    @Override
    public void findByEmail(
            FindByEmailGrpcRequest request,
            StreamObserver<FindByEmailGrpcResponse> responseObserver
    ) {
        FindByEmailGrpcResponse response = userQueryService.findByEmailWithRoles(request.getEmail())
                .map(userGrpcMapper::toFindByEmailResponse)
                .orElseGet(userGrpcMapper::emptyFindByEmailResponse);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void signUpOauth2(
            SignUpOauth2GrpcRequest request,
            StreamObserver<SignUpOauth2GrpcResponse> responseObserver
    ) {
        User newUser = oauth2UserSignUpService.signUp(
                request.getSub(),
                request.getEmail(),
                request.getNickname()
        );

        SignUpOauth2GrpcResponse response =
                userGrpcMapper.toSignUpOauth2Response(newUser);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}