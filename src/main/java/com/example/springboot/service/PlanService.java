package com.example.springboot.service;

import com.example.springboot.dao.repository.PlanRepository;
import com.example.springboot.service.validator.JsonSchemaValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minidev.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

@Service
public class PlanService {
    @Autowired
    PlanRepository planRepository;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public ResponseEntity<?> get(String id) {
        JsonNode plan = planRepository.getById(id);
        if (plan == null) { // Plan don't exist - 404
            return planDoNotExistResponse();
        }
        return ResponseEntity.ok().eTag(getEtag(plan)).body(plan); //Plan Exist - 200
    }

    public ResponseEntity<?> create(String jsonStr) {
        JsonSchemaValidator jsv = new JsonSchemaValidator();
        // JsonSchema is inValid - badRequest
        if (!jsv.validate(jsonStr, "plan")) {
            return JsonSchemaInvalidResponse();
        }
        // JsonSchema is Valid - 201 created
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // store into redis
            JsonNode plan = objectMapper.readTree(jsonStr);
            String planId = plan.at("/objectId").asText();
            planRepository.save(planId, plan);

            // send data to queue(RabbitMQ exchange)
            rabbitTemplate.convertAndSend("plan-exchange", "plan.create", plan);

            // return response
            JSONObject message = new JSONObject();
            message.put("objectId", planId);
            return ResponseEntity.created(null).eTag(getEtag(plan)).body(message);
        } catch (JsonProcessingException e) {
            return exceptionResponse(e);
        }
    }

    public ResponseEntity<?> patch(String jsonStr, String id, String etag) {
        ObjectNode plan = (ObjectNode) planRepository.getById(id);
        if (plan == null) { // Plan don't exist - 404
            return planDoNotExistResponse();
        }
        String originalEtag = getEtag(plan);
        etag = etag.replace("\"","");
        if (!originalEtag.equals(etag)) { // Etag does not match
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }
        JsonSchemaValidator jsv = new JsonSchemaValidator();
        if (!jsv.validate(jsonStr, "patchPlan")) { // JSON Schema is invalid - 400 Bad request
            return JsonSchemaInvalidResponse();
        }
        //Plan exist && Etag match && JSON Schema is valid
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode patchedPlan = objectMapper.readTree(jsonStr);
            Iterator<Map.Entry<String, JsonNode>> fields = patchedPlan.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String field = entry.getKey();
                JsonNode value = entry.getValue();
                if (field.equals("linkedPlanServices")) {
                    // update linkedPlanServices
                    ArrayNode oriLPSs = (ArrayNode) plan.at("/" + field);
                    ArrayNode patchedLPSs = (ArrayNode) value;
                    update(oriLPSs, patchedLPSs, "/objectId");
                    plan.replace(field, oriLPSs);
                } else {
                    // update immutable fields
                    String patchedValue = value.toString();
                    String originalValue = plan.at("/" + field).toString();
                    if (!patchedValue.equals(originalValue)) {
                        JSONObject message = new JSONObject();
                        message.put("error", "Attempted to update a field that should not be changed!");
                        return ResponseEntity.badRequest().body(message);
                    }
                }
            }
            planRepository.save(id, plan);

            JSONObject message = new JSONObject();
            message.put("message", "Resource updated!");
            return ResponseEntity.ok().eTag(getEtag(plan)).body(message);
        } catch (JsonProcessingException e) {
            return exceptionResponse(e);
        }
    }

    public ResponseEntity<?> delete(String id, String etag) {
        JsonNode plan = planRepository.getById(id);
        if (plan == null) { // Plan don't exist - 404
            return planDoNotExistResponse();
        }
        // Plan exist
        String originalEtag = getEtag(plan);
        etag = etag.replace("\"","");
        if (!originalEtag.equals(etag)) { // Etag does not match
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }
        planRepository.delete(id); //Delete Success - 204 NO CONTENT
        return ResponseEntity.noContent().build();
    }

    private String getEtag(JsonNode node) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String nodeJson = objectMapper.writeValueAsString(node);
            String etag = DigestUtils.md5DigestAsHex(nodeJson.getBytes());
            return etag;
        } catch (JsonProcessingException e) {
            System.out.println(new RuntimeException(e));
            return "";
        }
    }

    private JsonNode update(ArrayNode ori, ArrayNode patch, String idPath) {
        // Original linkedPlanServices
        ArrayList<String> oriIDs = new ArrayList<>(ori.size());
        for (int i = 0; i < ori.size(); i++) {
            JsonNode oriLPS = ori.get(i);
            String objectID = oriLPS.at(idPath).asText();
            oriIDs.add(i, objectID);
        }

        // Patched linkedPlanServices
        for (int i = 0; i < patch.size(); i++) {
            JsonNode patchedLPS = patch.get(i);
            String objectID = patchedLPS.at(idPath).asText();
            int index = oriIDs.indexOf(objectID);
            if (index == -1) { // if not found - add
                ori.add(patchedLPS);
            } else { // if found - update
                ori.set(index, patchedLPS);
            }
        }
        return ori;
    }

    private ResponseEntity<?> planDoNotExistResponse() {
        JSONObject message = new JSONObject();
        message.put("error", "Plan does not exist!");
        return ResponseEntity
                .status(404)
                .body(message);
    }

    private ResponseEntity<?> JsonSchemaInvalidResponse(){
        JSONObject message = new JSONObject();
        message.put("error", "Schema violations found. Please check your data format!");
        return ResponseEntity
                .badRequest()
                .body(message);
    }

    private ResponseEntity<?> exceptionResponse(Exception e){
        String message = new RuntimeException(e).toString();
        return ResponseEntity
                .badRequest()
                .body(message);
    }
}
