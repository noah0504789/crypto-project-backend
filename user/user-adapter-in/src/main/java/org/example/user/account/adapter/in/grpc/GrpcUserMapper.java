package org.example.user.account.adapter.in.grpc;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import org.example.grpc.user.GrpcFindByEmailResponse;
import org.example.grpc.user.GrpcSignUpOauth2Response;
import org.example.grpc.user.GrpcUser;
import org.example.user.account.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class GrpcUserMapper {

    public GrpcUser toGrpcUser(User user) {
        return GrpcUser.newBuilder()
                .setId(user.getPublicId().toString())
                .setSub(user.getSub())
                .setNickname(user.getNickname())
                .setEmail(user.getEmail())
                .addAllRoles(user.getRoleNames())
                .setCreatedAt(toProtoTimestamp(user))
                .build();
    }

    public GrpcFindByEmailResponse toFindByEmailResponse(User user) {
        return GrpcFindByEmailResponse.newBuilder()
                .setUser(toGrpcUser(user))
                .build();
    }

    public GrpcFindByEmailResponse emptyFindByEmailResponse() {
        return GrpcFindByEmailResponse.newBuilder()
                .build();
    }

    public GrpcSignUpOauth2Response toSignUpOauth2Response(User user) {
        return GrpcSignUpOauth2Response.newBuilder()
                .setUser(toGrpcUser(user))
                .build();
    }

    private Timestamp toProtoTimestamp(User user) {
        return Timestamps.fromMillis(user.toInstant().toEpochMilli());
    }
}
