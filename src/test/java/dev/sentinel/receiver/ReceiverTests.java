package dev.sentinel.receiver;

import java.nio.charset.StandardCharsets;

/** Dependency-free tests. Run with test.sh (uses java -ea). */
public final class ReceiverTests {
    private static int passed = 0;

    private static void check(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
        passed++;
        System.out.println("ok - " + name);
    }

    public static void main(String[] args) {
        // Official test vector from GitHub's webhook documentation.
        String secret = "It's a Secret to Everybody";
        byte[] body = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String expected = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

        check("sign matches GitHub test vector", Signature.sign(secret, body).equals(expected));
        check("verify accepts valid signature", Signature.verify(secret, body, expected));
        check("verify rejects wrong secret", !Signature.verify("other", body, expected));
        check("verify rejects tampered body",
                !Signature.verify(secret, "Hello, World?".getBytes(StandardCharsets.UTF_8), expected));
        check("verify rejects missing header", !Signature.verify(secret, body, null));
        check("verify rejects missing prefix", !Signature.verify(secret, body, expected.substring(7)));
        check("verify rejects empty secret", !Signature.verify("", body, expected));

        check("jsonEscape handles quotes and newlines",
                Forwarder.jsonEscape("a\"b\nc").equals("a\\\"b\\nc"));
        check("jsonEscape handles control chars", Forwarder.jsonEscape("\u0001").equals("\\u0001"));
        check("envelope wraps payload",
                Forwarder.envelope("push", "d1", "{\"x\":1}")
                        .equals("{\"source\":\"github\",\"event_type\":\"push\",\"delivery_id\":\"d1\",\"payload\":{\"x\":1}}"));
        check("envelope escapes hostile event type",
                !Forwarder.envelope("a\"}", "", "{}").contains("\"event_type\":\"a\"}"));

        System.out.println(passed + " tests passed");
    }
}
