package org.example.user.model;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import org.example.grpc.user.FindByEmailGrpcResponse;
import org.example.grpc.user.SignUpOauth2GrpcResponse;
import org.example.grpc.user.UserGrpc;
import org.example.user.model.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserGrpcMapper {

    public UserGrpc toUserGrpc(User user) {
        return UserGrpc.newBuilder()
                .setId(user.getPublicId().toString())
                .setSub(user.getSub())
                .setNickname(user.getNickname())
                .setEmail(user.getEmail())
                .addAllRoles(user.getRoleNames())
                .setCreatedAt(toProtoTimestamp(user))
                .build();
    }

    public FindByEmailGrpcResponse toFindByEmailResponse(User user) {
        return FindByEmailGrpcResponse.newBuilder()
                .setUser(toUserGrpc(user))
                .build();
    }

    public FindByEmailGrpcResponse emptyFindByEmailResponse() {
        return FindByEmailGrpcResponse.newBuilder()
                .build();
    }

    public SignUpOauth2GrpcResponse toSignUpOauth2Response(User user) {
        return SignUpOauth2GrpcResponse.newBuilder()
                .setUser(toUserGrpc(user))
                .build();
    }

    private Timestamp toProtoTimestamp(User user) {
        return Timestamps.fromMillis(user.toInstant().toEpochMilli());
    }
}
