package com.wbstack.queryservice;

import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

class Update {

    private static final String USER_AGENT = "WBStack - Query Service - Updater";

    private static String wbStackApiEndpoint;
    private static long wbStackSleepBetweenApiCalls;

    private static void setValuesFromEnvOrDie() {
        if( System.getenv("WBSTACK_API_ENDPOINT") == null || System.getenv("WBSTACK_BATCH_SLEEP") == null ) {
            System.err.println("WBSTACK_API_ENDPOINT and WBSTACK_BATCH_SLEEP environment variables must be set.");
            System.exit(1);
        }

        wbStackApiEndpoint = System.getenv("WBSTACK_API_ENDPOINT");
        wbStackSleepBetweenApiCalls = Integer.parseInt(System.getenv("WBSTACK_BATCH_SLEEP"));
    }

    public static void main(String[] args) throws InterruptedException {
        setValuesFromEnvOrDie();

        // TODO actually set to run for 1 hour or something?
        while (true) {
            long loopStarted = System.currentTimeMillis();
            mainLoop();
            sleepForRemainingTimeBetweenLoops( loopStarted );
        }

    }

    private static void mainLoop() {
        // Get the list of batches from the API
        String batchesApiResultString = null;
        URL obj = null;
        try {
            obj = new URL(wbStackApiEndpoint);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", USER_AGENT);
            int responseCode = con.getResponseCode();
            if( responseCode != 200 ) {
                System.err.println("Got non 200 response code: " + responseCode);
                return;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            batchesApiResultString = response.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (JsonElement batchElement : jsonStringToJsonArray( batchesApiResultString )) {
            runBatch( batchElement );
        }
    }

    private static void runBatch( JsonElement batchElement ) {
        // Get the values for the batch from the JSON
        JsonObject batch = batchElement.getAsJsonObject();
        String entityIDs = batch.get("entityIds").getAsString();
        JsonObject wiki = batch.get("wiki").getAsJsonObject();
        String domain = wiki.get("domain").getAsString();
        JsonObject wikiQsNamespace = wiki.get("wiki_queryservice_namespace").getAsJsonObject();
        String qsBackend = wikiQsNamespace.get("backend").getAsString();
        String qsNamespace = wikiQsNamespace.get("namespace").getAsString();

        // Run the main Update class with our altered args
        runUpdaterWithArgs(new String[] {
                "--sparqlUrl", "http://" + qsBackend + "/bigdata/namespace/" + qsNamespace + "/sparql",
                "--wikibaseHost", domain,
                "--wikibaseScheme", "https",
                "--conceptUri", "http://" + domain,
                "--entityNamespaces", "120,122,146",
                "--ids", entityIDs
        });
        // TODO on success maybe report back?
    }

    private static JsonArray jsonStringToJsonArray( String jsonString ) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement batches = null;
        Reader reader = new StringReader(jsonString);
        // Convert JSON to JsonElement, and later to String
        batches = gson.fromJson(reader, JsonElement.class);

        if (batches == null) {
            System.err.println("Failed to get JsonArray from jsonString (returning empty)");
            return new JsonArray();
        }

        return batches.getAsJsonArray();
    }

    private static void sleepForRemainingTimeBetweenLoops(long timeApiRequestDone ) throws InterruptedException {
        long secondsRunning = (System.currentTimeMillis() - timeApiRequestDone)/1000;
        if(secondsRunning < wbStackSleepBetweenApiCalls ) {
            TimeUnit.SECONDS.sleep(wbStackSleepBetweenApiCalls-secondsRunning);
        }
    }

    private static void runUpdaterWithArgs(String[] args) {
        System.out.print("Running updater with args: ");
        for(String s : args){
            System.out.print(s + " ");
        }
        System.out.println("");

        try {
            org.wikidata.query.rdf.tool.Update.main( args );
        } catch (Exception e) {
            System.err.println("Failed batch!");
            e.printStackTrace();
        }
    }
}
