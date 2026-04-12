package com.dojostay;

import com.dojostay.credentials.CredentialUploadProperties;
import com.dojostay.property.PropertyUploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        CredentialUploadProperties.class,
        PropertyUploadProperties.class
})
public class DojoStayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DojoStayApplication.class, args);
    }
}
