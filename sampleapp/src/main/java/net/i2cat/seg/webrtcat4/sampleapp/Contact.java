package net.i2cat.seg.webrtcat4.sampleapp;

public class Contact {
    private long id;
    private String name;
    private byte[] imageBytes;
    private String notifToken;

    public Contact(long id, String name, byte[] imageBytes, String notifToken) {
        this.id = id;
        this.imageBytes = imageBytes;
        this.name = name;
        this.notifToken = notifToken;
    }

    public long getId() {
        return id;
    }

    public byte[] getImage() {
        return imageBytes;
    }

    public String getName() {
        return name;
    }

    public String getNotificationToken() { return notifToken; }
}
