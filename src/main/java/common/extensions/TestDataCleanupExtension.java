package common.extensions;

import common.storage.TestDataRegistry;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TestDataCleanupExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        TestDataRegistry.cleanup();
    }
}
