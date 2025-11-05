package generators;

import org.apache.commons.lang3.RandomStringUtils;

public final class RandomData {
    private RandomData() {}

    public static String getUsername() {
        return RandomStringUtils.randomAlphabetic(10);
    }

    public static String getPassword() {
        return RandomStringUtils.randomAlphabetic(1).toUpperCase() +
                RandomStringUtils.randomAlphabetic(7).toLowerCase() +
                RandomStringUtils.randomNumeric(3) +
                "!";
    }
}
