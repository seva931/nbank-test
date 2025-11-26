package common.extensions;

import com.codeborne.selenide.Configuration;
import common.annotations.Browsers;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Arrays;

public class BrowserMatchExtension implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
        // Пытаемся получить аннотацию @Browsers с элемента (обычно тестовый метод)
        Browsers annotation = extensionContext.getElement()
                .map(el -> el.getAnnotation(Browsers.class))
                .orElse(null);
        if (annotation == null) {
            return ConditionEvaluationResult.enabled("Нет ограничений к браузеру");
        }

        String currentBrowser = Configuration.browser;

        // Тест выполняется, если текущий браузер есть среди допустимых
        boolean matches = Arrays.stream(annotation.value())
                .anyMatch(browser -> browser.equalsIgnoreCase(currentBrowser));

        if (matches) {
            return ConditionEvaluationResult.enabled(
                    "Текущий браузер удовлетворяет условия: " + currentBrowser
            );
        } else {
            return ConditionEvaluationResult.disabled(
                    "Тест пропущен, так как текущий браузер: " + currentBrowser +
                            " не находится в списке допустимых браузеров: " +
                            Arrays.toString(annotation.value())
            );
        }
    }
}
