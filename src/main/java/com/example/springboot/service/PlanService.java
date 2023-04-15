package com.example.springboot.service;

import com.example.springboot.dao.PlanRepository;
import com.example.springboot.service.validator.JsonSchemaValidator;
import com.example.springboot.util.ResponseEntityInstance;
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
    @Autowired
    private ObjectMapper objectMapper = new ObjectMapper();

    public ResponseEntity<?> get(String id) {
        JsonNode plan = planRepository.getById(id);
        if (plan == null) return ResponseEntityInstance.planDoNotExistResponse();// Plan don't exist - 404 Not Found
        return ResponseEntity.ok().eTag(getEtag(plan)).body(plan); // Plan Exist - 200 OK
    }

    public ResponseEntity<?> create(String jsonStr) {
        JsonSchemaValidator jsv = new JsonSchemaValidator();
        // JsonSchema is inValid - 400 Bad Request
        if (!jsv.validate(jsonStr, "plan")) return ResponseEntityInstance.JsonSchemaInvalidResponse();

        // JsonSchema is inValid, Store data into redis & send data to queue - 201 Created
        JsonNode plan = null;
        try {
            plan = objectMapper.readTree(jsonStr);
        }catch (JsonProcessingException e) {
            return ResponseEntityInstance.exceptionResponse(e);
        }
        String planId = plan.at("/objectId").asText();
        planRepository.save(planId, plan);
        rabbitTemplate.convertAndSend("plan-exchange", "create", jsonStr);
        return ResponseEntity.created(null).eTag(getEtag(plan)).body(getMessage("objectId", planId));
    }

    public ResponseEntity<?> put(String jsonStr, String id, String etag){
        ResponseEntity<?> checkOutput = patchOrPutCheck(jsonStr, id, etag);
        if (checkOutput!=null) return checkOutput;

        JsonNode oriPlan = planRepository.getById(id);
        JsonNode newPlan = null;
        try {
            newPlan = objectMapper.readTree(jsonStr);
        } catch (JsonProcessingException e) {
            return ResponseEntityInstance.exceptionResponse(e);
        }
        String newPlanId = newPlan.at("/objectId").asText();
        // Plan id does not match - 400 Bad Request
        if (!newPlanId.equals(id)) return ResponseEntityInstance.idNotMatch();
        // Plan exist && Etag match && Valid JSON Schema && Plan id match
        rabbitTemplate.convertAndSend("plan-exchange", "patch", id);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        planRepository.save(id, newPlan);
        return ResponseEntity.created(null).eTag(getEtag(newPlan)).body(getMessage("message","Updated successfully!"));
    }

    public ResponseEntity<?> patch(String jsonStr, String id, String etag) {
        ResponseEntity<?> checkOutput = patchOrPutCheck(jsonStr, id, etag);
        if (checkOutput!=null) return checkOutput;

        ObjectNode plan = (ObjectNode) planRepository.getById(id);
        //Plan exist && Etag match && JSON Schema is valid
        JsonNode patchedPlan = null;
        try {
            patchedPlan = objectMapper.readTree(jsonStr);
        } catch (JsonProcessingException e) {
            return ResponseEntityInstance.exceptionResponse(e);
        }

        Iterator<Map.Entry<String, JsonNode>> fields = patchedPlan.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String field = entry.getKey();
            JsonNode patchedValue = entry.getValue();
            if (field.equals("linkedPlanServices")) {
                // update linkedPlanServices
                ArrayNode oriLPSs = (ArrayNode) plan.at("/" + field);
                ArrayNode patchedLPSs = (ArrayNode) patchedValue;
                update(oriLPSs, patchedLPSs, "/objectId");
                plan.replace(field, oriLPSs);
            } else {
                // update immutable fields - 400 Bad Request
                String originalValue = plan.at("/" + field).toString();
                if (!patchedValue.toString().equals(originalValue)) {
                    return ResponseEntity.badRequest().body(getMessage("error", "Attempted to update a field that should not be changed!"));
                }
            }
        }
        planRepository.save(id, plan);
        rabbitTemplate.convertAndSend("plan-exchange", "patch", id);

        return ResponseEntity.ok().eTag(getEtag(plan)).body(getMessage("message", "Resource updated!"));
    }

    public ResponseEntity<?> delete(String id, String etag) {
        JsonNode plan = planRepository.getById(id);
        // Plan don't exist - 404 Not Found
        if (plan == null)  return ResponseEntityInstance.planDoNotExistResponse();
        // Etag does not match - 412 Precondition Failed
        String originalEtag = getEtag(plan);
        if (!originalEtag.equals(etag.replace("\"",""))) return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();

        // Plan exist && Etag match - 204 NO CONTENT
        rabbitTemplate.convertAndSend("plan-exchange", "delete", id);
        try {
            Thread.sleep(1000); // 等待1秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        planRepository.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String getEtag(JsonNode node) {
        try {
            String nodeJson = objectMapper.writeValueAsString(node);
            return DigestUtils.md5DigestAsHex(nodeJson.getBytes());
        } catch (JsonProcessingException e) {
            System.out.println(new RuntimeException(e));
            return "";
        }
    }
    public ResponseEntity<?> patchOrPutCheck(String jsonStr, String id, String etag){
        JsonNode oriPlan = planRepository.getById(id);
        if (planRepository.getById(id) == null) return ResponseEntityInstance.planDoNotExistResponse();
        // Etag does not match - 412 Precondition Failed
        String originalEtag = getEtag(oriPlan);
        if (!originalEtag.equals(etag.replace("\"",""))) return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        // JSON Schema is invalid - 400 Bad request
        JsonSchemaValidator jsv = new JsonSchemaValidator();
        if (!jsv.validate(jsonStr, "patchPlan")) return ResponseEntityInstance.JsonSchemaInvalidResponse();
        return null;
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

    private JSONObject getMessage(String message1, String message2) {
        JSONObject message = new JSONObject();
        message.put(message1, message2);
        return message;
    }
}
