package com.example.springboot.util;

import net.minidev.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseEntityInstance{

    public static ResponseEntity<?> planDoNotExistResponse(){
        JSONObject message = new JSONObject();
        message.put("message", "Plan don't exist");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message);
    }

    public static ResponseEntity<?> JsonSchemaInvalidResponse(){
        JSONObject message = new JSONObject();
        message.put("message", "JsonSchema is inValid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }

    public static ResponseEntity<?> exceptionResponse(Exception e){
        JSONObject message = new JSONObject();
        message.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message);
    }

    public static ResponseEntity<?> idNotMatch(){
        JSONObject message = new JSONObject();
        message.put("message", "Plan id in data does not match!");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message);
    }

}
