package org.psint.beyosclothing.modules.sms.config;

import net.adeonatech.service.SendSMSImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(EsmsProperties.class)
public class EsmsConfig {

    @Bean
    public SendSMSImpl sendSMSImpl() {
        return new SendSMSImpl();
    }

    /** Dedicated RestTemplate for the eSMS "SMS via GET request" (URL Message Key) endpoint. */
    @Bean
    public RestTemplate esmsRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
    }
}
