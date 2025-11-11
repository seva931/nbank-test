package iteration2test.ui;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static org.junit.jupiter.api.Assertions.fail;

public class LoginDiagnosticsTest {

    @BeforeAll
    static void localSetup() {
        Configuration.remote = null;                 // локальный браузер
        Configuration.browser = "chrome";
        Configuration.baseUrl = "http://localhost:3000";
        Configuration.pageLoadStrategy = "eager";
        Configuration.timeout = 8000;
        Configuration.headless = false;
    }

    @Test
    void login_full_diagnostics() {
        // 1) UI-логин
        Selenide.open("/");
        $(byAttribute("placeholder", "Username")).shouldBe(Condition.visible).setValue("admin");
        $(byAttribute("placeholder", "Password")).shouldBe(Condition.visible).setValue("admin");
        $(byText("Login")).shouldBe(Condition.visible, Condition.enabled).click();

        // 2) Ждём либо редирект, либо появление токена
        boolean redirected = waitUntil(() ->
                WebDriverRunner.url().contains("/admin"), 8);
        boolean tokenAppeared = waitUntil(() ->
                Boolean.TRUE.equals(Selenide.executeJavaScript("return !!localStorage.getItem('token')")), 8);

        // 3) Диагностика
        Map<String, Object> diag = new HashMap<>();
        diag.put("url", WebDriverRunner.url());
        diag.put("localStorage.token", Selenide.executeJavaScript("return localStorage.getItem('token')"));
        diag.put("document.cookie", Selenide.executeJavaScript("return document.cookie"));

        // 3.1) Локальный вызов логина: ДОЛЖНЫ быть Content-Type и тело '{}'
        Object authHdr = Selenide.executeAsyncJavaScript("""
          const cb = arguments[arguments.length - 1];
          fetch('/api/v1/auth/login', {
            method: 'POST',
            headers: {
              'Authorization': 'Basic ' + btoa('admin:admin'),
              'Content-Type': 'application/json'
            },
            body: '{}'
          })
          .then(r => Promise.all([r.status, r.headers.get('Authorization')]))
          .then(arr => cb({status: arr[0], authorization: arr[1]}))
          .catch(e => cb({status:-1, error: String(e)}));
        """);
        diag.put("login.fetch.status+Authorization", authHdr);

        // 3.2) Доступность API по host.docker.internal
        Object apiHealth = Selenide.executeAsyncJavaScript("""
          const cb = arguments[arguments.length - 1];
          fetch('http://host.docker.internal:4111/actuator/health')
            .then(r => cb(r.status))
            .catch(e => cb(-1));
        """);
        diag.put("api.health.from.browser", apiHealth);

        // 3.3) Если появился токен — проверяем /users/me
        Object meResp = Selenide.executeAsyncJavaScript("""
          const cb = arguments[arguments.length - 1];
          const t = localStorage.getItem('token');
          if (!t) { cb({skipped: true}); return; }
          fetch('/api/v1/users/me', { headers: { 'Authorization': t } })
            .then(async r => cb({status: r.status, body: await r.text()}))
            .catch(e => cb({status:-1, error:String(e)}));
        """);
        diag.put("/users/me.with.localStorage.token", meResp);

        // 4) Классификация
        String reason = classify(redirected, tokenAppeared, diag);

        // 5) Вывод и проверка
        System.out.println("=== LOGIN DIAGNOSTICS ===");
        diag.forEach((k,v) -> System.out.println(k + " = " + v));
        System.out.println("redirected=" + redirected + ", tokenAppeared=" + tokenAppeared);
        System.out.println("=== END DIAGNOSTICS ===");

        if (!redirected) {
            fail("Не произошло перенаправление на /admin. Причина: " + reason);
        }
        $("h1").shouldHave(Condition.exactText("Admin Panel"));
    }

    private static boolean waitUntil(Check cond, int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                if (Boolean.TRUE.equals(cond.ok())) return true;
            } catch (Throwable ignored) { }
            Selenide.sleep(200);
        }
        return false;
    }

    private interface Check { Boolean ok(); }

    private static String classify(boolean redirected, boolean tokenAppeared, Map<String, Object> d) {
        Object authObj = d.get("login.fetch.status+Authorization");
        int fetchStatus = -1;
        String fetchAuth = null;
        if (authObj instanceof Map<?,?> m) {
            Object st = m.get("status");
            Object ah = m.get("authorization");
            if (st instanceof Number n) fetchStatus = n.intValue();
            if (ah instanceof String s) fetchAuth = s;
        }
        String lsToken = String.valueOf(d.get("localStorage.token"));

        if (!tokenAppeared && (fetchAuth == null || "null".equals(fetchAuth))) {
            return "бэкенд не экспонирует заголовок Authorization до браузера. Проверить nginx UI: `proxy_pass_header Authorization;` и `add_header Access-Control-Expose-Headers Authorization always;`.";
        }
        if (!tokenAppeared && fetchAuth != null) {
            return "фронт получает Authorization, но не пишет token в localStorage. Проверить обработчик ответа логина.";
        }
        if (tokenAppeared && !redirected) {
            return "token есть (" + lsToken + "), но редиректа нет. Проверить роутинг после логина и права на /admin.";
        }
        if (redirected) {
            return "редирект есть, но контент /admin невалиден. Проверить H1 и наполнение страницы.";
        }
        return "не классифицировано. Проверить CORS, cookies, origin и сетевой доступ из контейнера.";
    }
}
