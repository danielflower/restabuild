package scaffolding;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;

import java.net.URLEncoder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class RestClient {

    public static RestClient create(String appRunnerUrl) {
        HttpClient c = new HttpClient();
        try {
            c.start();
            return new RestClient(c, appRunnerUrl);
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


    public ContentResponse release(String gitUrl) throws Exception {
        return client.GET(appRunnerUrl + "/release?gitUrl=" + URLEncoder.encode(gitUrl, "UTF-8"));
    }

    public void stop() {
        try {
            client.stop();
        } catch (Exception e) {
            // ignore
        }
    }
}
