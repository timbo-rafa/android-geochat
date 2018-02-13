package com.zv.geochat.model;

import java.util.Calendar;
import java.util.Date;

public class ChatMessage {
    private String id;
    private String userName;
    private String body;
    private Date date;

    public ChatMessage() {

    }

    public ChatMessage(String userName, String body) {
        this.userName = userName;
        this.body = body;
        this.date = Calendar.getInstance().getTime();
    }

    public String getUserName() {
        return userName;
    }

    public String getBody() {
        return body;
    }

    public String getId() {
        return id;
    }

    //public Date getDate() {return date;}

    public Long getDate() { return date.getTime();}

    public void setDate(Long date) {
        this.date = new Date(date);
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id='" + id + '\'' +
                ", userName='" + userName + '\'' +
                ", body='" + body + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}
