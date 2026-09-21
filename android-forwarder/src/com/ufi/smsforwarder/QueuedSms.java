package com.ufi.smsforwarder;

final class QueuedSms {
    final long id;
    final String sender;
    final long receivedAt;
    final String body;

    QueuedSms(long id, String sender, long receivedAt, String body) {
        this.id = id;
        this.sender = sender;
        this.receivedAt = receivedAt;
        this.body = body;
    }
}
