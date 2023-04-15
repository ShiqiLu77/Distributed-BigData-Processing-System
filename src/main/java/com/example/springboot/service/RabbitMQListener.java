package com.example.springboot.service;

import com.example.springboot.dao.PlanRepository;
import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;


@Component
public class RabbitMQListener{

    private static final String PLAN_CREATE_QUEUE = "plan.create";
    private static final String PLAN_PATCH_QUEUE = "plan.patch";
    private static final String PLAN_PUT_QUEUE = "plan.put";
    private static final String PLAN_DELETE_QUEUE = "plan.delete";
    private static final String PLAN_CREATE_BACKUP_QUEUE = "plan.create.backup";
    private static final String PLAN_PATCH_BACKUP_QUEUE = "plan.patch.backup";
    private static final String PLAN_PUT_BACKUP_QUEUE = "plan.put.backup";
    private static final String PLAN_DELETE_BACKUP_QUEUE = "plan.delete.backup";

    private static final String PLAN_INDEX_NAME = "plan";

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = PLAN_CREATE_QUEUE)
    public void createPlan(String plan){
        // Backup the plan data to the backup queue
        rabbitTemplate.convertAndSend(PLAN_CREATE_BACKUP_QUEUE, plan);

        // Save the plan data to Elasticsearch
        IndexCoordinates index = IndexCoordinates.of(PLAN_INDEX_NAME);
        boolean success = false;
        ArrayList<String[]> dataForES = convertData(plan);
        for ( String[] data : dataForES) {
            String Id = data[0];
            String document = data[1];
            // Save the plan data to Elasticsearch
            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(Id)
                    .withRouting("1")
                    .withSource(document)
                    .build();
            String documentId = elasticsearchOperations.index(indexQuery, index);
            if (!documentId.isEmpty()) success = true;
            else {
                System.out.println("Failed to insert document" + documentId);
                success = false;
                break;
            }
        }
        if (success) {
            rabbitTemplate.receiveAndConvert(PLAN_CREATE_BACKUP_QUEUE);
        }
        rabbitTemplate.receiveAndConvert(PLAN_CREATE_BACKUP_QUEUE);
    }

    @RabbitListener(queues = PLAN_PATCH_QUEUE)
    public void patchPlan(String planId){
        rabbitTemplate.convertAndSend(PLAN_PATCH_BACKUP_QUEUE, planId);

        JsonNode plan = (JsonNode) redisTemplate.opsForValue().get(planId);
        try {
            deletePlanAndItsChild(planId);
            createPlan(objectMapper.writeValueAsString(plan));
        } catch (IOException e) {
            System.out.println("patch & put Plan Error: " + e);
        }
        rabbitTemplate.receiveAndConvert(PLAN_PATCH_BACKUP_QUEUE);
    }

    @RabbitListener(queues = PLAN_DELETE_QUEUE)
    public void deletePlanAndItsChild(String planId) throws IOException{
        rabbitTemplate.convertAndSend(PLAN_DELETE_BACKUP_QUEUE, planId);

        RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));

        JsonNode plan = (JsonNode) redisTemplate.opsForValue().get(planId);
        ArrayList<String> documentIds = convertToKeys(plan);
        for (String documentId : documentIds) {
            System.out.print ("ID: " + documentId );
            DeleteRequest deleteRequest = new DeleteRequest(PLAN_INDEX_NAME,documentId);
            DeleteResponse deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT);
            if (deleteResponse.getResult().toString().equals("DELETED")) System.out.println("Successfully deleted document " + documentId);
            else System.out.println("Failed to delete document " + documentId);
        }

        rabbitTemplate.receiveAndConvert(PLAN_DELETE_BACKUP_QUEUE);
        client.close();
    }

    private ArrayList<String> convertToKeys(JsonNode plan){
        ArrayList<String> documentIds = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = plan.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (field.equals("linkedPlanServices")) {
                ArrayNode linkedPlanServices = (ArrayNode) value;
                for (JsonNode linkedPlanService : linkedPlanServices) {
                    // linkedPlanServices;
                    documentIds.add(linkedPlanService.at("/objectId").asText());
                    // linkedServices
                    JsonNode linkedService = linkedPlanService.at("/linkedService");
                    documentIds.add(linkedService.at("/objectId").asText());
                    // planserviceCostShares
                    JsonNode planserviceCostShares = linkedPlanService.at("/planserviceCostShares");
                    documentIds.add(planserviceCostShares.at("/objectId").asText());
                }
            } else if(field.equals("planCostShares")){
                // planCostShares
                String pcsId = value.at("/objectId").asText();
                documentIds.add(pcsId);
            }else if(field.equals("objectId")) {
                // plan
                documentIds.add(value.asText());
            }
        }
        return documentIds;
    }
    private String createElasticSearchData(String type, String parentID, String data){
        String joinField;
        if (parentID == null) joinField = "\"join_field\": \""+ type+ "\"";
        else joinField = "\"join_field\": {  \"name\": \"" + type + "\", \"parent\": \"" + parentID + "\" }";

        int lastIndex = data.lastIndexOf("}");
        return data.substring(0, lastIndex) +","+ joinField + "}";
    }
    private ArrayList<String[]> convertData(String planStr){
        // Convert the plan data to the format required by Elasticsearch
        ArrayList<String[]> dataForES= new ArrayList<>();

        JsonNode planJsonNode;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            planJsonNode =objectMapper.readTree(planStr);
            // plan
            String planId = planJsonNode.at("/objectId").asText();
            String planData = "{\"" + planStr.substring(planStr.lastIndexOf("_org"));
            dataForES.add(new String[]{planId, createElasticSearchData("plan",null, planData)});

            Iterator<Map.Entry<String, JsonNode>> fields = planJsonNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String field = entry.getKey();
                JsonNode value = entry.getValue();
                if (field.equals("linkedPlanServices")) {
                    ArrayNode linkedPlanServices = (ArrayNode) value;
                    for (JsonNode linkedPlanService : linkedPlanServices) {
                        // linkedPlanServices
                        String lpsStr = objectMapper.writeValueAsString(linkedPlanService);
                        lpsStr = "{\"" + lpsStr.substring(lpsStr.lastIndexOf("_org"));
                        String lpsId = linkedPlanService.at("/objectId").asText();
                        dataForES.add(new String[]{lpsId, createElasticSearchData("linkedPlanServices", planId, lpsStr)});
                        // linkedServices
                        JsonNode linkedService = linkedPlanService.at("/linkedService");
                        String lsStr  = objectMapper.writeValueAsString(linkedService);
                        String lsId = linkedService.at("/objectId").asText();
                        dataForES.add(new String[]{lsId, createElasticSearchData("linkedService", lpsId, lsStr)});
                        // planserviceCostShares
                        JsonNode planserviceCostShares = linkedPlanService.at("/planserviceCostShares");
                        String pscsStr  = objectMapper.writeValueAsString(planserviceCostShares);
                        String pscsId = planserviceCostShares.at("/objectId").asText();
                        dataForES.add(new String[]{pscsId, createElasticSearchData("planserviceCostShares", lpsId, pscsStr)});
                    }
                } else if (field.equals("planCostShares")){
                    // planCostShares
                    String pcsStr  = objectMapper.writeValueAsString(value);
                    String pcsId = value.at("/objectId").asText();
                    dataForES.add(new String[]{pcsId, createElasticSearchData("planCostShares", planId, pcsStr)});
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return dataForES;
    }

    private ArrayList<String[]> convertDataOLD(String planStr){
        // Convert the plan data to the format required by Elasticsearch
        ArrayList<String[]> dataForES= new ArrayList<>();

        JsonNode planJsonNode;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            planJsonNode =objectMapper.readTree(planStr);
            // plan
            String planId = planJsonNode.at("/objectId").asText();
            String plandata;
            dataForES.add(new String[]{planId, createElasticSearchData("plan",null, planStr)});

            Iterator<Map.Entry<String, JsonNode>> fields = planJsonNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String field = entry.getKey();
                JsonNode value = entry.getValue();
                if (field.equals("linkedPlanServices")) {
                    ArrayNode linkedPlanServices = (ArrayNode) value;
                    for (JsonNode linkedPlanService : linkedPlanServices) {
                        // linkedPlanServices
                        String lpsStr = objectMapper.writeValueAsString(linkedPlanService);
                        String lpsId = linkedPlanService.at("/objectId").asText();
                        String lpsdata = createElasticSearchData("linkedPlanServices", planId, lpsStr);
                        dataForES.add(new String[]{lpsId, createElasticSearchData("linkedPlanServices", planId, lpsStr)});

                        // linkedServices
                        JsonNode linkedService = linkedPlanService.at("/linkedService");
                        String lsStr  = objectMapper.writeValueAsString(linkedService);
                        String lsId = linkedService.at("/objectId").asText();
                        dataForES.add(new String[]{lsId, createElasticSearchData("linkedService", lpsId, lsStr)});
                        // planserviceCostShares
                        JsonNode planserviceCostShares = linkedPlanService.at("/planserviceCostShares");
                        String pscsStr  = objectMapper.writeValueAsString(planserviceCostShares);
                        String pscsId = planserviceCostShares.at("/objectId").asText();
                        dataForES.add(new String[]{pscsId, createElasticSearchData("planserviceCostShares", lpsId, pscsStr)});
                    }
                } else if (field.equals("planCostShares")){
                    // planCostShares
                    String pcsStr  = objectMapper.writeValueAsString(value);
                    String pcsId = value.at("/objectId").asText();
                    dataForES.add(new String[]{pcsId, createElasticSearchData("planCostShares", planId, pcsStr)});
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return dataForES;
    }
}


