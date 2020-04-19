package ch.uzh.ifi.seal.soprafs20.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "MESSAGE")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long messageId;

    @Column(nullable = false)
    private Long authorId;

    @Column
    private String authorUsername;

    @NotBlank
    @NotEmpty
    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    @JsonFormat(pattern="hh:mm:ss")
    private Date creationDate;

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorUsername() { return authorUsername; }

    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    @JsonFormat(pattern="hh:mm:ss")
    public void setCreationDate() {
        this.creationDate = new Date();
    }
}
