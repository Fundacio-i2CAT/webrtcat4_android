package net.i2cat.seg.webrtcat4;

public enum WebRTCatErrorCode {
    CANT_JOIN_ROOM(101),
    CANT_CONNECT_TO_SIGNALING_SERVER(102),
    CANT_MESSAGE_ROOM(103),
    ICE_CONNECTION_FAILED(104),
    INTERNAL_STATE_MACHINE_ERROR(105),
    SIGNALING_SERVER_CONNECTION_ERROR(201),
    SIGNALING_SERVER_CONNECTION_CLOSED(202),
    SIGNALING_SERVER_REPORTED_ERROR(301),
    UNKNOWN_SIGNALING_SERVER_MESSAGE(302),
    GENERAL_ERROR(500);

    private int code;

    WebRTCatErrorCode(int code) {
        this.code = code;
    }

    public int getIntCode() {
        return code;
    }
}
