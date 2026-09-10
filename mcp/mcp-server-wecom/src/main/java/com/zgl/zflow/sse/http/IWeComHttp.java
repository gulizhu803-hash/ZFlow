package com.zgl.zflow.sse.http;

import com.zgl.zflow.sse.dto.GetAccessTokenResponse;
import com.zgl.zflow.sse.dto.SendTextCardHttpRequest;
import com.zgl.zflow.sse.dto.SendMessageHttpResponse;
import com.zgl.zflow.sse.dto.SendTextHttpRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface IWeComHttp {
    @GET("cgi-bin/gettoken")
    Call<GetAccessTokenResponse> getAccessToken(
            @Query("corpid") String corpId,
            @Query("corpsecret") String corpSecret);

    @POST("cgi-bin/message/send")
    Call<SendMessageHttpResponse> sendTextCard(
            @Body SendTextCardHttpRequest request,
            @Query("access_token") String accessToken);

    @POST("cgi-bin/message/send")
    Call<SendMessageHttpResponse> sendText(
            @Body SendTextHttpRequest httpRequest,
            @Query("access_token") String accessToken);
}
