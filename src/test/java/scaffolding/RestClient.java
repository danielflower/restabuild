package scaffolding;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;

import java.net.URLEncoder;

public class RestClient {

    public static RestClient create(String serverUrl) {
        HttpClient c = new HttpClient();
        try {
            c.start();
            return new RestClient(c, serverUrl);
        } catch (Exception e) {
            throw new RuntimeException("Unable to make client", e);
        }
    }

    private final HttpClient client;
    private final String appRunnerUrl;

    private RestClient(HttpClient client, String appRunnerUrl) {
        this.client = client;
        this.appRunnerUrl = appRunnerUrl;
    }


    public ContentResponse build(String gitUrl) throws Exception {
        return client.POST(appRunnerUrl + "/v1/builds?gitUrl=" + URLEncoder.encode(gitUrl, "UTF-8")).send();
    }

    public void stop() {
        try {
            client.stop();
        } catch (Exception e) {
            // ignore
        }
    }
}
