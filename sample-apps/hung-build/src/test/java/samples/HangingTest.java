package com.danielflower.restabuild.build;

import org.junit.Test;

public class HangingTest {

    @Test
    public void itHangs() throws InterruptedException {
        System.out.println("Going to sleep now");
        Thread.sleep(10 * 60 * 1000);
    }
}
