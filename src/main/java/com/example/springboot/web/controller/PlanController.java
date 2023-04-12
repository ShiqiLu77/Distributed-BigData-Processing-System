package com.example.springboot.web.controller;

import com.example.springboot.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
public class PlanController{
    @Autowired
    PlanService planService;

    @GetMapping("/plan/{id}/")
    public ResponseEntity<?> get(@PathVariable String id){
        return planService.get(id);
    }

    @PostMapping(value = "/plan/save/", produces = "application/json")
    public ResponseEntity<?> create(@RequestBody String jsonStr){
        return planService.create(jsonStr);
    }

    @PatchMapping("/plan/patch/{id}/")
    public ResponseEntity<?> patch(@RequestBody String jsonStr, @PathVariable String id, @RequestHeader("If-Match") String etag){
        return planService.patch(jsonStr, id, etag);
    }

    @DeleteMapping("/plan/delete/{id}/")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public ResponseEntity<?> delete(@PathVariable String id, @RequestHeader("If-Match") String etag){
        return planService.delete(id, etag);
    }
}