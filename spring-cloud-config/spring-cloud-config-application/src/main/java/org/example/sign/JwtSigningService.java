package org.example.sign;

import lombok.RequiredArgsConstructor;
import org.example.vault.VaultTransitSignResult;
import org.example.vault.VaultTransitSignPort;
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
