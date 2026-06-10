package com.techiguru.jenu.api;

public class ChatRequest {
    private String prompt;

    public ChatRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() { return prompt; }
}