package common.extensions;

import api.configs.Config;
import common.annotations.Device;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Arrays;

public class DeviceMatchExtension implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Device annotation = context.getElement()
                .map(el -> el.getAnnotation(Device.class))
                .orElse(null);

        // Если аннотации нет — тест без ограничений по типу устройства
        if (annotation == null) {
            return ConditionEvaluationResult.enabled("Нет ограничений к устройству");
        }

        // Текущий тип устройства берём из конфига, например device=desktop/mobile
        String currentDevice = Config.getProperty("device");
        if (currentDevice == null || currentDevice.isBlank()) {
            return ConditionEvaluationResult.enabled("Тип устройства не задан в конфигурации");
        }

        boolean matches = Arrays.stream(annotation.value())
                .anyMatch(device -> device.equalsIgnoreCase(currentDevice));

        if (matches) {
            return ConditionEvaluationResult.enabled(
                    "Текущее устройство удовлетворяет условиям: " + currentDevice
            );
        } else {
            return ConditionEvaluationResult.disabled(
                    "Тест пропущен: текущее устройство " + currentDevice +
                            " не входит в допустимые: " + Arrays.toString(annotation.value())
            );
        }
    }
}
