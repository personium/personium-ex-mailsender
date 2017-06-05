/**
 * personium.io
 * Copyright 2014 FUJITSU LIMITED
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.personium.engine.extension.mailsender;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;

import com.dumbster.smtp.SimpleSmtpServer;
import com.dumbster.smtp.SmtpMessage;
import io.personium.engine.extension.support.ExtensionLogger;

public class Ext_MailSenderTest {

    // SMTP mock object.
    SimpleSmtpServer server = null;
    Field smtpHostField = null;
    Field smtpPortField = null;

    @BeforeClass
    public static void beforeClass() {
        Ext_MailSender.setLogger(Ext_MailSender.class, new ExtensionLogger(Ext_MailSender.class));
    }

    @Before
    public void before() throws Exception {
        // default port is 25, but changed to 1025 for testing.;
        server = SimpleSmtpServer.start(1025);

        smtpHostField = Ext_MailSender.class.getDeclaredField("smtpHost");
        smtpPortField = Ext_MailSender.class.getDeclaredField("smtpPort");
        smtpHostField.setAccessible(true);
        smtpPortField.setAccessible(true);
    }

    @After
    public void after() {
        server.stop();
    }

    /**
     * SMTPの設定が存在しない場合例外を発すること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void SMTPの設定が存在しない場合例外を発すること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();

        NativeObject reqJson = null;
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(0));
    }

    /**
     * リクエストにNULLを渡した場合例外を発すること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void リクエストにNULLを渡した場合例外を発すること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject reqJson = null;
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(0));
    }

    /**
     * 宛先が全く指定されていない場合例外を発生すること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void 宛先が全く指定されていない場合例外を発生すること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {}));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {}));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {}));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {}));
        reqJson.put("from", reqJson, new NativeObject());
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "ISO-2022-JP");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(0));
    }

    /**
     * 宛先各種1件毎を指定してメール送信できること_charset未指定.
     * @throws Exception
     */
    @Test
    public void 宛先各種1件毎を指定してメール送信できること_charset未指定() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        // reqJson.put("charset", reqJson, "ISO-2022-JP");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?ISO-2022-JP?"));

            assertThat(message.getHeaderValues("To").length, is(1));
            assertTrue(message.getHeaderValue("To").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValues("Cc").length, is(1));
            assertTrue(message.getHeaderValue("Cc").startsWith("=?ISO-2022-JP?"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("quoted-printable"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * 宛先各種1件毎を指定してメール送信できること_ISO_2022_JP.
     * @throws Exception
     */
    @Test
    public void 宛先各種1件毎を指定してメール送信できること_ISO_2022_JP() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "ISO-2022-JP");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?ISO-2022-JP?"));

            assertThat(message.getHeaderValues("To").length, is(1));
            assertTrue(message.getHeaderValue("To").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValues("Cc").length, is(1));
            assertTrue(message.getHeaderValue("Cc").startsWith("=?ISO-2022-JP?"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("quoted-printable"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * toに不正な値を指定した場合にメールアドレス不正の例外が上がること.
     * @throws Exception
     */
    @Test
    public void toに不正な値を指定した場合にメールアドレス不正の例外が上がること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "MailTest1010");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());

        try {
        	mailTransport.send(reqJson);
        	fail("EcmaError not throwed.");
        } catch (EcmaError e) {
            assertThat(e.getErrorMessage(), is("Invalid mail address is detected."));
        }
    }

    /**
     * toにマルチバイト文字列を指定した場合にメールアドレス不正の例外が上がること.
     * @throws Exception
     */
    @Test
    public void toにマルチバイト文字列を指定した場合にメールアドレス不正の例外が上がること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "あいうえお@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());

        try {
            mailTransport.send(reqJson);
            fail("EcmaError not throwed.");
        } catch (EcmaError e) {
            assertThat(e.getErrorMessage(), is("Invalid mail address is detected."));
        }
    }

    /**
     * ccに不正な値を指定した場合にメールアドレス不正の例外が上がること.
     * @throws Exception
     */
    @Test
    public void ccに不正な値を指定した場合にメールアドレス不正の例外が上がること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "MailTest1010");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());

        try {
            mailTransport.send(reqJson);
            fail("EcmaError not throwed.");
        } catch (EcmaError e) {
            assertThat(e.getErrorMessage(), is("Invalid mail address is detected."));
        }
    }

    /**
     * bccに不正な値を指定した場合にメールアドレス不正の例外が上がること.
     * @throws Exception
     */
    @Test
    public void bccに不正な値を指定した場合にメールアドレス不正の例外が上がること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "MailTest1010");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());

        try {
            mailTransport.send(reqJson);
            fail("EcmaError not throwed.");
        } catch (EcmaError e) {
            assertThat(e.getErrorMessage(), is("Invalid mail address is detected."));
        }
    }

    /**
     * fromに不正な値を指定した場合にメールアドレス不正の例外が上がること.
     * @throws Exception
     */
    @Test
    public void fromに不正な値を指定した場合にメールアドレス不正の例外が上がること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "MailTest1010");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject replyto = new NativeObject();
        replyto.put("address", replyto, "bob111@example.com");
        replyto.put("name", replyto, "Bob Smith");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {replyto }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());

        try {
            mailTransport.send(reqJson);
            fail("EcmaError not throwed.");
        } catch (EcmaError e) {
            assertThat(e.getErrorMessage(), is("Invalid mail address is detected."));
        }
    }

    /**
     * reply-toに不正な値を指定した場合にメールアドレス不正の例外が上がること.
     * @throws Exception
     */
    @Test
    public void replytoに不正な値を指定した場合にメールアドレス不正の例外が上がること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject replyto = new NativeObject();
        replyto.put("address", replyto, "MailTest1010");
        replyto.put("name", replyto, "Bob Smith");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {replyto }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());

        try {
            mailTransport.send(reqJson);
            fail("EcmaError not throwed.");
        } catch (EcmaError e) {
            assertThat(e.getErrorMessage(), is("Invalid mail address is detected."));
        }
    }

    /**
     * 宛先各種1件毎を指定してメール送信できること_UTF_8.
     * @throws Exception
     */
    @Test
    public void 宛先各種1件毎を指定してメール送信できること_UTF_8() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?UTF-8?"));

            assertThat(message.getHeaderValues("To").length, is(1));
            assertTrue(message.getHeaderValue("To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Cc").length, is(1));
            assertTrue(message.getHeaderValue("Cc").startsWith("=?UTF-8?"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("base64"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * 宛先各種合計50件を指定してメール送信できること.
     * @throws Exception
     */
    @Test
    @Ignore
    // SimpleSMTPServerのバグにより正しい assertionができないため、スキップ
    public void 宛先各種合計50件を指定してメール送信できること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?UTF-8?"));

            assertThat(message.getHeaderValues("To").length, is(20));
            assertTrue(message.getHeaderValue("To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Cc").length, is(20));
            assertTrue(message.getHeaderValue("Cc").startsWith("=?UTF-8?"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("7bit"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * 宛先各種合計51件を指定して制限値超えでエラーとなること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void 宛先各種合計51件を指定して制限値超えでエラーとなること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 21)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);
    }

    /**
     * ReplyTo_50件を指定してメール送信できること.
     * @throws Exception
     */
    @Test
    @Ignore
    // SimpleSMTPServerのバグにより正しい assertionができないため、スキップ
    public void ReplyTo_50件を指定してメール送信できること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject[] sender100 = new NativeObject[100];
        Arrays.fill(sender100, sender);

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        reqJson.put("reply-to", reqJson, new NativeArray(Arrays.copyOfRange(sender100, 0, 50)));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?UTF-8?"));

            assertThat(message.getHeaderValues("To").length, is(20));
            assertTrue(message.getHeaderValue("To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Cc").length, is(20));
            assertTrue(message.getHeaderValue("Cc").startsWith("=?UTF-8?"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(50));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("7bit"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * ReplyTo_51件を指定して制限値超えでエラーとなること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void ReplyTo_51件を指定して制限値超えでエラーとなること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject[] sender100 = new NativeObject[100];
        Arrays.fill(sender100, sender);

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        reqJson.put("reply-to", reqJson, new NativeArray(Arrays.copyOfRange(sender100, 0, 51)));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);
    }

    /**
     * 必須プロパティ不足_From_でエラーとなること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void 必須プロパティ不足_From_でエラーとなること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        // reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);
    }

    /**
     * 必須プロパティ不足_ReplyTo_でエラーとなること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void 必須プロパティ不足_ReplyTo_でエラーとなること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        // reqJson.put("reply-to", new NativeArray(new Object[] {sender}));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);
    }

    /**
     * 必須プロパティ不足_Subject_でエラーとなること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void 必須プロパティ不足_Subject_でエラーとなること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        // reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);
    }

    /**
     * 必須プロパティ不足_Text_でエラーとなること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void 必須プロパティ不足_Text_でエラーとなること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject[] recpients100 = new NativeObject[100];
        Arrays.fill(recpients100, recipient);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("cc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 20)));
        reqJson.put("bcc", reqJson, new NativeArray(Arrays.copyOfRange(recpients100, 0, 10)));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        // reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);
    }

    /**
     * 宛先の表示名が省略されていてもてメール送信できること_UTF_8.
     * @throws Exception
     */
    @Test
    public void 宛先の表示名が省略されていてもてメール送信できること_UTF_8() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        // recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?UTF-8?"));

            assertThat(message.getHeaderValues("To").length, is(1));
            assertTrue(message.getHeaderValue("To").equals("taro1@example.com"));
            assertThat(message.getHeaderValues("Cc").length, is(1));
            assertTrue(message.getHeaderValue("Cc").equals("taro1@example.com"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("base64"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * 宛先の表示名にnullが指定されていてもてメール送信できること_UTF_8.
     * @throws Exception
     */
    @Test
    public void 宛先の表示名にnullが指定されていてもてメール送信できること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, null);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?UTF-8?"));

            assertThat(message.getHeaderValues("To").length, is(1));
            assertTrue(message.getHeaderValue("To").equals("taro1@example.com"));
            assertThat(message.getHeaderValues("Cc").length, is(1));
            assertTrue(message.getHeaderValue("Cc").equals("taro1@example.com"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("base64"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * 宛先のaddressが省略されている場合エラーとなること.
     * @throws Exception
     */
    @Test(expected = EcmaError.class)
    public void 宛先のaddressが省略されている場合エラーとなること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        // recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);
    }

    /**
     * 宛先各種1件毎を指定してメール送信できること_ISO_2022_JP.
     * @throws Exception
     */
    @Test
    @Ignore
    // テストのテスト用なのでスキップ
    public void 宛先各種1件毎を指定してメール送信できること_temp() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "26");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, "Taro Yamada");

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient, sender }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));

        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "ISO-2022-JP");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?ISO-2022-JP?"));

            assertThat(message.getHeaderValues("To").length, is(1));
            assertTrue(message.getHeaderValue("To").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValues("Cc").length, is(1));
            assertTrue(message.getHeaderValue("Cc").startsWith("=?ISO-2022-JP?"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?ISO-2022-JP?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("quoted-printable"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

    /**
     * カスタムヘッダが指定されていなくてもメール送信できること.
     * @throws Exception
     */
    @Test
    public void カスタムヘッダが指定されていなくてもメール送信できること() throws Exception {
        Ext_MailSender mailTransport = new Ext_MailSender();
        smtpHostField.set(mailTransport, "localhost");
        smtpPortField.set(mailTransport, "1025");

        NativeObject sender = new NativeObject();
        sender.put("address", sender, "john999@example.com");
        sender.put("name", sender, "John Smith");

        NativeObject recipient = new NativeObject();
        recipient.put("address", recipient, "taro1@example.com");
        recipient.put("name", recipient, null);

        NativeObject reqJson = new NativeObject();
        reqJson.put("to", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("cc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("bcc", reqJson, new NativeArray(new Object[] {recipient }));
        reqJson.put("reply-to", reqJson, new NativeArray(new Object[] {sender }));
        reqJson.put("from", reqJson, sender);
        reqJson.put("subject", reqJson, "メール件名");
        reqJson.put("text", reqJson, "メール本文\n本文だよ。");
        reqJson.put("charset", reqJson, "UTF-8");
        reqJson.put("envelope-from", reqJson, "john@example.com");
        // reqJson.put("headers", reqJson, new NativeObject());
        mailTransport.send(reqJson);

        assertThat(server.getReceivedEmailSize(), is(1));
        @SuppressWarnings("rawtypes")
        Iterator mails = server.getReceivedEmail();
        while (mails.hasNext()) {
            SmtpMessage message = (SmtpMessage) mails.next();
            System.out.println(message.toString());

            assertThat(message.getHeaderValue("Subject"), is(notNullValue()));
            assertTrue(message.getHeaderValue("Subject").startsWith("=?UTF-8?"));

            assertThat(message.getHeaderValues("To").length, is(1));
            assertTrue(message.getHeaderValue("To").equals("taro1@example.com"));
            assertThat(message.getHeaderValues("Cc").length, is(1));
            assertTrue(message.getHeaderValue("Cc").equals("taro1@example.com"));
            // BCCの宛先はSMTPサーバ上では見えない？
            // assertThat(message.getHeaderValues("Bcc").length, is(1));
            assertThat(message.getHeaderValues("From").length, is(1));
            assertTrue(message.getHeaderValue("From").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValues("Reply-To").length, is(1));
            assertTrue(message.getHeaderValue("Reply-To").startsWith("=?UTF-8?"));
            assertThat(message.getHeaderValue("Content-Transfer-Encoding"), is("base64"));
            assertThat(message.getHeaderValue("Date"), is(notNullValue()));

            assertThat(message.getBody(), is(notNullValue()));
        }
    }

}
