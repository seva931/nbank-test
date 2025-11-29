package common.storage;

import api.models.AccountResponse;
import api.models.CreateUserResponse;
import api.requests.steps.AccountSteps;
import api.requests.steps.AdminSteps;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.List;

public class TestDataRegistry {

    private static final ThreadLocal<TestDataRegistry> INSTANCE =
            ThreadLocal.withInitial(TestDataRegistry::new);

    private final List<CreateUserResponse> users = new ArrayList<>();
    private final List<AccountEntry> accounts = new ArrayList<>();

    private TestDataRegistry() {
    }

    private static TestDataRegistry getInstance() {
        return INSTANCE.get();
    }

    private static class AccountEntry {
        private final RequestSpecification spec;
        private final long accountId;

        private AccountEntry(RequestSpecification spec, long accountId) {
            this.spec = spec;
            this.accountId = accountId;
        }
    }

    // --- публичный API ---

    public static void registerUser(CreateUserResponse userResponse) {
        if (userResponse == null) {
            return;
        }
        getInstance().users.add(userResponse);
    }

    public static void registerAccount(RequestSpecification spec, AccountResponse account) {
        if (spec == null || account == null) {
            return;
        }
        getInstance().accounts.add(new AccountEntry(spec, account.getId()));
    }

    public static void cleanup() {
        TestDataRegistry registry = getInstance();
        registry.cleanupAccounts();
        registry.cleanupUsers();
        registry.clear();
    }

    // --- внутренние методы ---

    private void cleanupAccounts() {
        for (AccountEntry entry : accounts) {
            try {
                AccountSteps.deleteAccount(entry.spec, entry.accountId);
            } catch (Exception ignored) {
                // не валим тесты из-за неуспешного удаления
            }
        }
    }

    private void cleanupUsers() {
        for (CreateUserResponse user : users) {
            try {
                AdminSteps.deleteUser(user.getId());
            } catch (Exception ignored) {
                // то же самое для пользователей
            }
        }
    }

    private void clear() {
        accounts.clear();
        users.clear();
    }
}
