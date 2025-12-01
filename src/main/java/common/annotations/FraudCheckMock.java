package common.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FraudCheckMock {

    int port() default 8090;

    // Паттерн для пути запроса, по умолчанию перехватываем любой POST на этот порт
    String endpoint() default ".*";

    String status();

    String decision();

    double riskScore();

    String reason();

    boolean requiresManualReview();

    boolean additionalVerificationRequired();
}
