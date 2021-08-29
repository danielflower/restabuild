package scaffolding;

import org.hamcrest.Matcher;
import org.junit.Assert;

import static org.hamcrest.MatcherAssert.assertThat;

public class AssertUtil {
    public static <T> void assertEventually(Func<T> actual, Matcher<? super T> matcher) {
        assertEventually("", actual, matcher);
    }
    public static <T> void assertEventually(String reason, Func<T> actual, Matcher<? super T> matcher) {
        for (int i = 0; i < 100; i++) {
            try {
                T val = actual.apply();
                if (matcher.matches(val)) {
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException("Finishing early", e);
            }
        }
        try {
            assertThat(reason, actual.apply(), matcher);
        } catch (Exception e) {
            Assert.fail("Lambda threw exception: " + e);
        }
    }

    public interface Func<V> {
        V apply() throws Exception;
    }
}
