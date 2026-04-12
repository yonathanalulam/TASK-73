package com.dojostay.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "post_comments")
public class PostComment {

    public enum Status { PUBLISHED, HIDDEN, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(nullable = false, length = 2000)
    private String body;

    /** When non-null, this comment is a direct reply to the referenced comment. */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    /** When non-null, this comment quotes the referenced comment verbatim for context. */
    @Column(name = "quoted_comment_id")
    private Long quotedCommentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PUBLISHED;

    @Column(name = "hidden_reason")
    private String hiddenReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    /** Set when a previously hidden comment is restored via moderation. */
    @Column(name = "restored_at")
    private Instant restoredAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }
    public Long getQuotedCommentId() { return quotedCommentId; }
    public void setQuotedCommentId(Long quotedCommentId) { this.quotedCommentId = quotedCommentId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getHiddenReason() { return hiddenReason; }
    public void setHiddenReason(String hiddenReason) { this.hiddenReason = hiddenReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getHiddenAt() { return hiddenAt; }
    public void setHiddenAt(Instant hiddenAt) { this.hiddenAt = hiddenAt; }
    public Instant getRestoredAt() { return restoredAt; }
    public void setRestoredAt(Instant restoredAt) { this.restoredAt = restoredAt; }
}
