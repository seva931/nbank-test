package iteration2test;

import java.util.UUID;

public class TestDataFactory {

    public static UserCredentials generateUser() {
        String username = "user_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        String password = "Pass!" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6);

        return new UserCredentials(username, password);
    }
}
