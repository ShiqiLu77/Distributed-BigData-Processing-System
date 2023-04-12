package com.example.springboot.service.validator;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;

public class JsonSchemaValidator {
    private final String planJsonSchema = "/planJsonSchema.json";
    private final String patchPlanJsonSchema = "/patchPlanJsonSchema.json";

    public boolean validate(String jsonStr, String mode) {
        String path = null;
        if (mode.equals("plan")) {
            path = planJsonSchema;
        }else if (mode.equals("patchPlan")){
            path = patchPlanJsonSchema;
        }

        InputStream inputStreamJsonSchema = getClass().getResourceAsStream(path);
        JSONObject jsonSchema = new JSONObject(new JSONTokener(inputStreamJsonSchema));
        Schema schema = SchemaLoader.load(jsonSchema);
        try{
            JSONObject data = new JSONObject(jsonStr);
            schema.validate(data);
            return true;
        }catch (org.json.JSONException | ValidationException e){
            System.out.println( new RuntimeException(e));
            return false;
        }
    }
}
