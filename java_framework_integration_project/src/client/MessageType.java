package client;

public enum MessageType {
    FREE,
    BUSY,
    DATA,
    SENDING,
    DONE_SENDING,
    DATA_SHORT,
    END,
    HELLO,
    TOKEN_ACCEPTED,
    TOKEN_REJECTED
}