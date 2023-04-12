package com.example.springboot.web.tokenvalidator;

import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;


import java.util.Base64;


@Component
public class JwtTokenValidator {

    private static final String ISSUER = "https://accounts.google.com";
    private static final String AUDIENCE = "620494849714-unoe41g93ieqod0isbft7f7hcer8vlag.apps.googleusercontent.com";
    private static final String SUBJECT = "113150970185658905491";
    //private static final int EXPIRATION_IN_SECONDS = 60; // 60s

    // exponent of public key
    private static final String e = "AQAB";

    // modulus of public key
    private static final String n = "r54td3hTv87IwUNhdc-bYLIny4tBVcasvdSd7lbJILg58C4DJ0RJPczXd_rlfzzYGvgpt3Okf_anJd5aah196P3bqwVDdelcDYAhuajBzn40QjOBPefvdD5zSo18i7OtG7nhAhRSEGe6Pjzpck3wAogqYcDgkF1BzTsRB-DkxprsYhp5pmL5RnX-6EYP5t2m9jJ-_oP9v1yvZkT5UPb2IwOk5GDllRPbvp-aJW_RM18ITU3qIbkwSTs1gJGFWO7jwnxT0QBaFD8a8aev1tmR50ehK-Sz2ORtvuWBxbzTqXXL39qgNJaYwZyW-2040vvuZnaGribcxT83t3cJlQdMxw";


    public static Object[] verifyToken(String token) {
        Object[] output = new Object[2];
        try {
            // Construct a public key
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                    new BigInteger(1, Base64.getUrlDecoder().decode(n)),
                    new BigInteger(1, Base64.getUrlDecoder().decode(e))
            );
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // Use RS256 algorithm with public key
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) publicKey, null);

            // Verify JWT by RS256 algorithm
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .withAudience(AUDIENCE)
                    .withSubject(SUBJECT)
                    .build();

            DecodedJWT jwt = verifier.verify(token);
            output[0] = true;
        } catch (JWTDecodeException | NoSuchAlgorithmException | InvalidKeySpecException | AlgorithmMismatchException |
                 SignatureVerificationException e) {
            System.out.println("JwtTokenValidator error: " + e);
            output[0] = false;
            output[1] = "Invalid Token!";
        } catch (TokenExpiredException e){
            output[0] = false;
            output[1] = "Expired Token!";
        } finally {
            return output;
        }
    }
}
