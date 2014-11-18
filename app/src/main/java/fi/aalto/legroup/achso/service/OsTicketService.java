package fi.aalto.legroup.achso.service;

import java.util.HashMap;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Header;
import retrofit.http.Headers;
import retrofit.http.POST;

/**
 * Created by lassi on 14.11.14.
 */
public interface OsTicketService {

    @Headers("User-Agent: AchSo! Android")
    @POST("/api/tickets.json")
    public void sendFeedback(@Header("X-API-Key") String apiKey, @Body HashMap<String, String> map,
                      Callback<String> callback);

}
