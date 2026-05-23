package org.example.jwks.adapter.in;

import lombok.RequiredArgsConstructor;
import org.example.jwks.JwksService;
import org.example.sign.JwtSigningService;
import org.example.sign.dto.SignRequest;
import org.example.sign.dto.SignResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwksService jwksService;
    private final JwtSigningService jwtSigningService;

    @GetMapping("${api-path.jwks:/.well-known/jwks.json}")
    public Map<String, Object> jwks(@RequestParam("keyName") String keyName) {
        return jwksService.getJwks(keyName);
    }

    @PostMapping("${api-path.sign:/sign}")
    public SignResponse sign(@RequestBody SignRequest request) {
        return jwtSigningService.sign(request);
    }
}
