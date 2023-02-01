package com.novetta.clavin.extractor;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Data object for AdaptNLP's built in Entity JSON structures
 */
public class Entity {
	private String text;
	
	@JsonProperty("start_pos")
	private int startPos;
	
	@JsonProperty("end_pos")
	private int endPos;
	
	private String type;
	
	private float confidence; 	
	
	
	/*
	 * getters/setters
	 */
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public int getStartPos() {
		return startPos;
	}
	public void setStartPos(int startPos) {
		this.startPos = startPos;
	}
	public int getEndPos() {
		return endPos;
	}
	public void setEndPos(int endPos) {
		this.endPos = endPos;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public float getConfidence() {
		return confidence;
	}
	public void setConfidence(float confidence) {
		this.confidence = confidence;
	}
}
