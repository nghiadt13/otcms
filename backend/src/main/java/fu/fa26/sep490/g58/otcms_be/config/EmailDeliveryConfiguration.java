package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.util.Properties;

@Configuration
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
public class EmailDeliveryConfiguration {
    @Bean
    JavaMailSender javaMailSender(EmailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());
        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.transport.protocol", "smtp");
        mail.put("mail.smtp.auth", "true");
        mail.put("mail.smtp.starttls.enable", "true");
        mail.put("mail.smtp.starttls.required", "true");
        mail.put("mail.smtp.connectiontimeout", String.valueOf(properties.connectTimeoutMs()));
        mail.put("mail.smtp.timeout", String.valueOf(properties.readTimeoutMs()));
        mail.put("mail.smtp.writetimeout", String.valueOf(properties.writeTimeoutMs()));
        return sender;
    }
}
