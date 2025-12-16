package com.joe.aichat;

import com.volcengine.ark.runtime.service.ArkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AichatApplicationTests {

    @Autowired
    private ArkService arkService;

    @Test
    void contextLoads() {
        System.out.println("ArkService 是否成功注入: " + (arkService != null));
    }

}
