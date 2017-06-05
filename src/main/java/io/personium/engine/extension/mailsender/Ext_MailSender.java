/**
 * Personium
 * Copyright 2016 FUJITSU LIMITED
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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;

import io.personium.engine.extension.support.AbstractExtensionScriptableObject;
import io.personium.engine.extension.support.ExtensionErrorConstructor;

/**
 * Engine-Extension メール送信機能.
 */
@SuppressWarnings("serial")
public class Ext_MailSender extends AbstractExtensionScriptableObject {

    private static final String JAVAMAIL_SMTP_FROM_KEY = "mail.smtp.from";
    private static final String JAVAMAIL_SMTP_PORT_KEY = "mail.smtp.port";
    private static final String JAVAMAIL_SMTP_HOST_KEY = "mail.smtp.host";
    private static final String EXT_MAILSENDER_SMTP_HOST = "io.personium.engine.extension.MailSender.smtp.host";
    private static final String EXT_MAILSENDER_SMTP_PORT = "io.personium.engine.extension.MailSender.smtp.port";

    private static final String DEFAULT_SMTP_PORT = "25";

    private static final String DEFAULT_BODY_ENCODING = "ISO-2022-JP";
    private static final int MAX_RECIPIENTS = 50;
    private static final int MAX_REPLY_TO = 50;

    private String smtpHost = null;
    private String smtpPort = null;

    /**
     * JavaScriptへの公開名.
     */
    @Override
    public String getClassName() {
        return "MailSender";
    }

    /**
     * コンストラクタ.
     */
    @JSConstructor
    public Ext_MailSender() {
        smtpHost = getProperties().getProperty(EXT_MAILSENDER_SMTP_HOST);
        smtpPort = getProperties().getProperty(EXT_MAILSENDER_SMTP_PORT, DEFAULT_SMTP_PORT);

        if (null == smtpHost || smtpHost.isEmpty()) {
            String message = "smtp host is not specified in configuration.";
            this.getLogger().warn(message);
        }
    }

    // /**
    // * JavaScript用コンストラクタ.
    // */
    // public void jsConstructor() throws EcmaError {
    // if (null == smtpHost || smtpHost.isEmpty()) {
    // String message = "smtp host is not specified in configuration.";
    // this.getLogger().info(message);
    // throw ExtensionException.constructError(message);
    // }
    // }

    /**
     * 引数で指定された JSONの記述に従い、メールを送信する.
     * @param reqJson メール送信内容の JSON
     * @throws Exception リクエスト内容の不備、メール送信時のエラー
     */
    @JSFunction
    public void send(NativeObject reqJson) throws EcmaError {

        if (null == smtpHost || smtpHost.isEmpty()) {
            String message = "smtp host is not specified in configuration.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }

        if (null == reqJson) {
            String message = "Invalid argument for send method.: null.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }

        // Request(JSON)の解析
        String charset = retrieveObjectAs(String.class, reqJson, "charset");
        if (null == charset || charset.isEmpty()) {
            charset = DEFAULT_BODY_ENCODING;
        }

        NativeArray to = retrieveObjectAs(NativeArray.class, reqJson, "to");
        InternetAddress[] toAddresses = getInternetAddresses(to, charset);

        NativeArray cc = retrieveObjectAs(NativeArray.class, reqJson, "cc");
        InternetAddress[] ccAddresses = getInternetAddresses(cc, charset);

        NativeArray bcc = retrieveObjectAs(NativeArray.class, reqJson, "bcc");
        InternetAddress[] bccAddresses = getInternetAddresses(bcc, charset);

        NativeArray replyTo = retrieveObjectAs(NativeArray.class, reqJson, "reply-to");
        InternetAddress[] replyToAddresses = getInternetAddresses(replyTo, charset);

        NativeObject from = retrieveObjectAs(NativeObject.class, reqJson, "from");
        InternetAddress fromAddress = null;
        if (null != from) { // 必須チェック
            fromAddress = parseJsonAsInternetAddress(from, charset);
        }

        String subject = retrieveObjectAs(String.class, reqJson, "subject");
        String mailBody = retrieveObjectAs(String.class, reqJson, "text");
        String envelopeFrom = retrieveObjectAs(String.class, reqJson, "envelope-from");

        // カスタムヘッダへの対応
        NativeObject headers = retrieveObjectAs(NativeObject.class, reqJson, "headers");

        validateRequests(toAddresses, ccAddresses, bccAddresses, replyToAddresses, fromAddress, subject, mailBody);

        // ここからが JavaMailによる送信処理
        Properties prop = new Properties();
        prop.put(JAVAMAIL_SMTP_HOST_KEY, smtpHost);
        prop.put(JAVAMAIL_SMTP_PORT_KEY, smtpPort);

        // envelope-fromを設定する場合
        if (null != envelopeFrom && !envelopeFrom.isEmpty()) {
            prop.put(JAVAMAIL_SMTP_FROM_KEY, envelopeFrom);
        }

        Session session = Session.getInstance(prop);
        // session.setDebug(true);

        // 送信メッセージを生成
        MimeMessage objMsg = new MimeMessage(session);
        try {
            // 送信先（TOのほか、CCやBCCも設定可能）
            // TO
            if (toAddresses != null && 0 < toAddresses.length) {
                objMsg.setRecipients(Message.RecipientType.TO, toAddresses);
            }
            // CC
            if (ccAddresses != null && 0 < ccAddresses.length) {
                objMsg.setRecipients(Message.RecipientType.CC, ccAddresses);
            }
            // BCC
            if (bccAddresses != null && 0 < bccAddresses.length) {
                objMsg.setRecipients(Message.RecipientType.BCC, bccAddresses);
            }
            // Reply-To
            if (replyToAddresses != null && 0 < replyToAddresses.length) {
                objMsg.setReplyTo(replyToAddresses);
            }

            // Fromヘッダ
            objMsg.setFrom(fromAddress);
            // 件名
            objMsg.setSubject(subject, charset);

            // 本文
            objMsg.setText(mailBody, charset);
            objMsg.setSentDate(new Date());

            // カスタムヘッダへの対応
            if (null != headers) {
                for (Entry<Object, Object> entry : headers.entrySet()) {
                    if (null == entry.getKey() || null == entry.getValue()) {
                        continue;
                    }
                    objMsg.setHeader(entry.getKey().toString(), entry.getValue().toString());
                }
            }

            // Updates the appropriate header fields of this message
            // to be consistent with the message's contents.
            objMsg.saveChanges();

        } catch (MessagingException e) {
            // ここまでは、SMTPサーバへの送信前なので、クリティカルな状態にはないと考えている。このためログレベルは INFO.
            String message = "Invalid message content/configuration were detected.";
            this.getLogger().info(message, e);
            String errorMessage = String.format("%s Cause: [%s]", message, e.getMessage());
            throw ExtensionErrorConstructor.construct(errorMessage);
        }

        try {
            // メール送信
            // この APIでの失敗は、SMTPサーバとの接続後の問題であるため、warnレベルでログを出しておく。
            Transport.send(objMsg);
        } catch (SendFailedException e) {
            String message = "Message could not be sent to some recipients.";
            this.getLogger().warn(message, e);
            String errorMessage = String.format("%s Cause: [%s]", message, e.getMessage());
            throw ExtensionErrorConstructor.construct(errorMessage);

        } catch (MessagingException e) {
            String message = "Failed to send message.";
            this.getLogger().warn(message, e);
            String errorMessage = String.format("%s Cause: [%s]", message, e.getMessage());
            throw ExtensionErrorConstructor.construct(errorMessage);
        }

    }

    private void validateRequests(InternetAddress[] toAddresses,
            InternetAddress[] ccAddresses,
            InternetAddress[] bccAddresses,
            InternetAddress[] replyToAddresses,
            InternetAddress fromAddress,
            String subject,
            String mailBody) throws EcmaError {

        // 制限: to/cc/bcc 合計 50件
        // reply-To 合計 50件
        // fromAddress, subject, mailBody 省略不可

        int addressCount = 0;
        if (null != toAddresses) {
            addressCount += toAddresses.length;
        }
        if (null != ccAddresses) {
            addressCount += ccAddresses.length;
        }
        if (null != bccAddresses) {
            addressCount += bccAddresses.length;
        }
        if (MAX_RECIPIENTS < addressCount) {
            String message = String.format("Number of recipients exceeds the limit(%d).", MAX_RECIPIENTS);
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }
        if (0 == addressCount) {
            String message = "No mail recipients are specified in request.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }

        if (null == replyToAddresses || 0 == replyToAddresses.length) {
            String message = "At least one reply-to address is required.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }
        if (MAX_REPLY_TO < replyToAddresses.length) {
            String message = String.format("Number of reply-to addresses exceeds the limitation(%d)", MAX_REPLY_TO);
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }

        // fromAddress, subject, mailBody 省略不可
        if (null == fromAddress) {
            String message = "From address is not specified.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }
        if (null == subject || subject.isEmpty()) {
            String message = "Empty subject is not permitted.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }
        if (null == mailBody || mailBody.isEmpty()) {
            String message = "Empty mail body is not permitted.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }
    }

    private InternetAddress[] getInternetAddresses(NativeArray jsonMultiAddress, String charset)
            throws EcmaError {
        if (null == jsonMultiAddress || 0 == jsonMultiAddress.size()) {
            return null;
        }
        List<InternetAddress> result = new ArrayList<InternetAddress>();
        for (int i = 0; i < jsonMultiAddress.size(); i++) {
            NativeObject jsonSingleAddress = castTo(NativeObject.class, jsonMultiAddress.get(i));
            InternetAddress address = parseJsonAsInternetAddress(jsonSingleAddress, charset);
            if (null != address) {
                result.add(address);
            }
        }
        return result.toArray(new InternetAddress[] {});
    }

    private InternetAddress parseJsonAsInternetAddress(NativeObject jsonSingleAddress, String charset)
            throws EcmaError {
        String address = castTo(String.class, jsonSingleAddress.get("address", jsonSingleAddress));
        String name = castTo(String.class, jsonSingleAddress.get("name", jsonSingleAddress));

        if (address == null || address.isEmpty()) {
            String message = "'address' field is not specified or empty.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }
        try {
        	InternetAddress iAddress = new InternetAddress(address, true);
        	if (null != name && !name.isEmpty()) {
        	    iAddress.setPersonal(name, charset);
        	}
        	return iAddress;
        } catch (UnsupportedEncodingException e) {
            String message = "Unsupported encoding is specified for mail display name.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        } catch (AddressException e) {
            String message = "Invalid mail address is detected.";
            this.getLogger().info(message);
            throw ExtensionErrorConstructor.construct(message);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T retrieveObjectAs(Class<T> clazz, NativeObject source, String key) throws EcmaError {
        if (null != source && null != key && !key.isEmpty()) {
            Object obj = source.get(key, source);
            if (null == obj || NOT_FOUND == obj) {
                return null;
            }
            if (clazz.isAssignableFrom(obj.getClass())) {
                return (T) obj;
            }
        }
        String message = String.format("Invalid JSON. Object associated with key '%s' does not have required type.",
                key);
        this.getLogger().info(message);
        throw ExtensionErrorConstructor.construct(message);
    }

    @SuppressWarnings("unchecked")
    private <T> T castTo(Class<T> clazz, Object obj) throws EcmaError {
        if (null == obj || NOT_FOUND == obj) {
            return null;
        }
        if (clazz.isAssignableFrom(obj.getClass())) {
            return (T) obj;
        }
        String message = String
                .format("Invalid JSON. Failed to cast an object to required type (%s).", clazz.getName());
        this.getLogger().info(message);
        throw ExtensionErrorConstructor.construct(message);
    }
}
