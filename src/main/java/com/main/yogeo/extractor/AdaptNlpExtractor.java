package com.novetta.clavin.extractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.lang.Math;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;


public class AdaptNlpExtractor implements LocationExtractor {
	private String host;
	private int port;
	private ObjectMapper jsonMapper = new ObjectMapper();
	private SentenceDetectorME sentenceDetector;
	private static final String ENDPOINT = "/api/token_tagger";
	private static final CloseableHttpClient client = HttpClients.createDefault();
	private static final String PATH_TO_SENTENCE_DETECTOR_MODEL = "/en-sent.bin";
	private static final Logger LOG = LoggerFactory.getLogger(AdaptNlpExtractor.class);
	
	/**
	 * Number of sentences to parse at a time. If you are getting 400 bad request errors when calling AdaptNLP, it's probably
	 * because the request is too large; you can fix it by making this number smaller in exchange for a small performance penalty
	 */
	private static final int CHUNK_SIZE = 10;
	
	
	/**
	 *  Default with connection to locally hosted AdaptNLP
	 *  
     * @throws IOException	throws exception on error processing text
	 */
	public AdaptNlpExtractor() throws IOException {
		host = "http://localhost";
		port = 5000;
		jsonMapper.getFactory().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);
		sentenceDetector = new SentenceDetectorME(
				new SentenceModel(ApacheExtractor.class.getResourceAsStream(PATH_TO_SENTENCE_DETECTOR_MODEL)));
	}
	
	public AdaptNlpExtractor(String host, int port) {
		this.host = host;
		this.port = port;
	}

	@Override
	public List<LocationOccurrence> extractLocationNames(String plainText) {
		String errorCopy = null;
		try {
			
			// parse ten sentences at a time
			List<LocationOccurrence> locations = new ArrayList<>();
			Span[] sentenceSpans = sentenceDetector.sentPosDetect(plainText);
			for (int i=0; i<sentenceSpans.length / CHUNK_SIZE; ++i) {
				// get the sentences we're actually performing NER on right now
				int startIndex = i * CHUNK_SIZE;
				int stopIndex = Math.min(startIndex + CHUNK_SIZE, sentenceSpans.length);
				TextBody textBody = new TextBody(plainText.substring(
						sentenceSpans[startIndex].getStart(),
						sentenceSpans[stopIndex].getEnd()
				));

				// make call
				HttpPost request = new HttpPost(host + ":" + port + ENDPOINT);
				request.addHeader("Content-Type", "application/json");
				String body = jsonMapper.writeValueAsString(textBody);
				request.setEntity(new StringEntity(body));
				CloseableHttpResponse response = client.execute(request);
				HttpEntity responseEntity = response.getEntity();
				
				// parse JSON response
				String responseString = EntityUtils.toString(responseEntity);
				JsonNode responseJson = jsonMapper.readTree(responseString);
				if (responseJson.has("detail")) {
					LOG.warn("Failed to parse entities from \"" + textBody.getText() + "\"");
				}
				else {
					errorCopy = responseJson.toString();
					ArrayNode entitiesJson = (ArrayNode)responseJson.get(0).get("entities");
					for (JsonNode entityJson : entitiesJson) {
						String entityString = entityJson.toString();
						Entity entity = jsonMapper.readValue(entityString, Entity.class);
						
						// GPE = geo-political entity; i.e. a city/state/country, etc. 
						if (entity.getType().equals("GPE")) {
							locations.add(new LocationOccurrence(entity.getText(), entity.getStartPos()));
						}
					}
				}
			}
			return locations;
		} catch (Exception e) {
			LOG.error("Failed to parse " + errorCopy);
			return null;
		}
	}
}
