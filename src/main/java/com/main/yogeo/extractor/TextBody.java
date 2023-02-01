package com.novetta.clavin.extractor;

public class TextBody {
	private String text;
	
	public TextBody(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return "TextBody(\"" + text + "\")";
	}
}