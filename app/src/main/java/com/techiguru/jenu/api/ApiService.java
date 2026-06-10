package com.techiguru.jenu.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("v1/chat")
    Call<ChatResponse> getChatResponse(@Body ChatRequest request);
}