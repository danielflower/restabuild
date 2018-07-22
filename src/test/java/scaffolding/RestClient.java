package scaffolding;

import org.eclipse.jetty.client.HttpClient;

public class RestClient {

    public static HttpClient instance = create();

    private static HttpClient create() {
        HttpClient c = new HttpClient();
        c.setFollowRedirects(false);
        try {
            c.start();
            return c;
        } catch (Exception e) {
            throw new RuntimeException("Unable to make client", e);
        }
    }

    private RestClient() {}

}
