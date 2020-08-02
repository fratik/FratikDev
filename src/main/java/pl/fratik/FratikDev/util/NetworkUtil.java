/* kradzione z fbota v3 bo jestem leniwym śmierdzielem */
package pl.fratik.FratikDev.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class NetworkUtil {
    private static final String USER_AGENT = "FratikDev/" + NetworkUtil.class.getPackage().getImplementationVersion() + " (https://fratikbot.pl)";
    private static OkHttpClient client = new OkHttpClient();
    private static Gson gson = new Gson();

    public static byte[] download(String url, String authorization) throws IOException {
        Request.Builder req = new Request.Builder()
                .header("User-Agent", USER_AGENT);
        if (authorization != null) req = req.header("Authorization", authorization);
        req = req.url(url);
        Response res = client.newCall(req.build()).execute();
        return res.body() == null ? new byte[0] : res.body().bytes();
    }

    public static Response headRequest(String url) throws IOException {
        return client.newCall(new Request.Builder().head().header("User-Agent", USER_AGENT).url(url).build()).execute();
    }

    public static Response postRequest(String url, MediaType type, String content, String authorization)  throws IOException {
        Request.Builder req = new Request.Builder()
                .header("User-Agent", USER_AGENT);
        if (authorization != null) req = req.header("Authorization", authorization);
        req = req.url(url)
                .post(RequestBody.create(type, content));
        return client.newCall(req.build()).execute();
    }

    public static JsonObject getJson(String url, String authorization) throws IOException {
        Request req = new Request.Builder()
                .header("User-Agent", USER_AGENT)
                .header("Authorization", authorization)
                .url(url)
                .build();
        Response res = client.newCall(req).execute();
        return res.body() == null ? null : gson.fromJson(res.body().string(), JsonObject.class);
    }

    public static byte[] download(String url) throws IOException {
        Request req = new Request.Builder()
                .header("User-Agent", USER_AGENT)
                .url(url)
                .build();
        Response res = client.newCall(req).execute();
        return res.body() == null ? new byte[0] : res.body().bytes();
    }

    public static byte[] getBytesFromBufferArray(int[] bufferArray) {
        byte[] arr = new byte[bufferArray.length];
        for (int i = 0; i < bufferArray.length; i++) {
            arr[i] = Integer.valueOf(bufferArray[i]).byteValue();
        }
        return arr;
    }

    public static byte[] getBytesFromBufferArray(JsonArray bufferArray) {
        byte[] arr = new byte[bufferArray.size()];
        for (int i = 0; i < bufferArray.size(); i++) {
            arr[i] = Integer.valueOf(bufferArray.get(i).getAsInt()).byteValue();
        }
        return arr;
    }

    /**
     * Decodes the passed UTF-8 String using an algorithm that's compatible with
     * JavaScript's <code>decodeURIComponent</code> function. Returns
     * <code>null</code> if the String is <code>null</code>.
     *
     * @param s The UTF-8 encoded String to be decoded
     * @return the decoded String
     */
    public static String decodeURIComponent(String s)
    {
        if (s == null)
        {
            return null;
        }

        String result = null;

        try
        {
            result = URLDecoder.decode(s, "UTF-8");
        }

        // This exception should never occur.
        catch (UnsupportedEncodingException e)
        {
            result = s;
        }

        return result;
    }

    /**
     * Encodes the passed String as UTF-8 using an algorithm that's compatible
     * with JavaScript's <code>encodeURIComponent</code> function. Returns
     * <code>null</code> if the String is <code>null</code>.
     *
     * @param s The String to be encoded
     * @return the encoded String
     */
    public static String encodeURIComponent(String s)
    {
        String result = null;

        try
        {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        }

        // This exception should never occur.
        catch (UnsupportedEncodingException e)
        {
            result = s;
        }

        return result;
    }
}

