# personium-ex-mailsender

## About

This is a [Personium](http://personium.io)'s Engine extension for sending SMTP emails.

## Usage

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
        "charset": "ISO-2022-JP"
    };
    var sender = new _p.extension.MailSender();
    sender.send(mailObj);
    return {
        status : 200,
        headers : {"Content-Type":"application/json"},
        body : ['Mailsend Complete!']
    };
}
```

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

Copyright 2016 FUJITSU LIMITED
```
