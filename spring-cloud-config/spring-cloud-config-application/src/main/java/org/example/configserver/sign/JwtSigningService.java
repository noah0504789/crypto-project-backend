package org.example.configserver.sign;

import lombok.RequiredArgsConstructor;
import org.example.configserver.sign.dto.SignRequest;
import org.example.configserver.sign.dto.SignResponse;
import org.example.configserver.vault.domain.VaultTransitSignResult;
import org.example.configserver.vault.port.out.VaultTransitSignPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtSigningService {

    private final JwtSigningInputDigester jwtSigningInputDigester;
    private final VaultTransitSignPort vaultTransitSignPort;
    private final VaultSignatureParser vaultSignatureParser;

    public SignResponse sign(SignRequest request) {
        String digestB64 = jwtSigningInputDigester.digestToBase64(
                request.headerB64u(),
                request.payloadB64u()
        );

        VaultTransitSignResult result = vaultTransitSignPort.sign(
                request.keyName(),
                request.keyVersion(),
                digestB64
        );

        String sigB64u = vaultSignatureParser.toBase64Url(result.signature());

        String kid = request.keyName() + ":" + result.keyVersion();

        return new SignResponse(
                kid,
                "RS256",
                sigB64u
        );
    }
}
