# personium-ex-mailsender

## About

This is a [Personium](http://personium.io)'s Engine extension for sending SMTP emails.

## Usage

1. Clone this repository.
2. Compile the source codes to `Ext_MailSender.jar` by maven command `mvn package -DskipTests=true`.
3. Edit [property file](https://github.com/personium/personium-ex-mailsender/blob/master/conf/Ext_MailSender.properties) to set the accessible SMTP server's host name and the port number.
4. Set the jar module `Ext_MailSender.jar` and the property file `Ext_MailSender.properties` into the Engine Extension directory in Personium application server. Default directory is `/personium/personium-engine/extensions/`.
5. Restart tomcat process.
6. Set an Engine Service which call this mail-sending function.

Sample script is below.
```
function(request) {
    var mailObj = {
        "to":[{"name":"John Doe","address":"john.doe@example.com"}],
        "from": {
            "address" : "admin@personium.io",
            "name"    : "Admin Office"
        },
        "reply-to": [{
            "address" : "admin@personium.io",
            "name"    : "Admin Office"
        }],
        "envelope-from": "admin@personium.io",
        "subject": "Greetings",
        "text": "Hello\n Thank you!",
        "charset": "UTF-8"
    };
    // in case of Japanese
    // "charset": "ISO-2022-JP"
    var sender = new _p.extension.MailSender();
    // Call mail send method.
    sender.send(mailObj);
    return {
        status : 200,
        headers : {"Content-Type":"application/json"},
        body : ['MailSender Complete!']
    };
}
```

7. Grant the `D:exec` privilege to using role and call Engine Execution API by the user who has this role.

## License

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
