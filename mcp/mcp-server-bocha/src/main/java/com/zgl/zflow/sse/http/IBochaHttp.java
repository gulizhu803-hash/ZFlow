package com.zgl.zflow.sse.http;

import com.zgl.zflow.sse.dto.BochaWebSearchHttpRequest;
import com.zgl.zflow.sse.dto.BochaWebSearchHttpResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface IBochaHttp {

    @Headers("Content-Type: application/json")
    @POST("v1/web-search")
    Call<BochaWebSearchHttpResponse> webSearch(
            @Header("Authorization") String authorization,
            @Body BochaWebSearchHttpRequest request
    );

}
