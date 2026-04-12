package com.dojostay.remediation;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.community.CommunityService;
import com.dojostay.community.ModerationService;
import com.dojostay.community.ModerationReport;
import com.dojostay.community.PostCommentRepository;
import com.dojostay.community.PostLikeRepository;
import com.dojostay.community.PostRepository;
import com.dojostay.community.UserBlockRepository;
import com.dojostay.community.UserFollowRepository;
import com.dojostay.community.ThreadMuteRepository;
import com.dojostay.community.PostMentionRepository;
import com.dojostay.community.dto.CommentResponse;
import com.dojostay.community.dto.CreateCommentRequest;
import com.dojostay.community.dto.CreateModerationReportRequest;
import com.dojostay.community.dto.CreatePostRequest;
import com.dojostay.community.dto.FollowResponse;
import com.dojostay.community.dto.LikeStatusResponse;
import com.dojostay.community.dto.PostResponse;
import com.dojostay.community.dto.ResolveReportRequest;
import com.dojostay.community.Post;
import com.dojostay.roles.UserRoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D11 — C8 community features test.
 * Verifies: threaded replies, likes, follows, blocks, mutes, moderation restore.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class CommunityFeaturesIT {

    private static final Long ORG = 801L;

    @Autowired private CommunityService communityService;
    @Autowired private ModerationService moderationService;
    @Autowired private PostRepository postRepo;
    @Autowired private PostCommentRepository commentRepo;
    @Autowired private PostLikeRepository likeRepo;
    @Autowired private UserFollowRepository followRepo;
    @Autowired private UserBlockRepository blockRepo;
    @Autowired private ThreadMuteRepository muteRepo;
    @Autowired private PostMentionRepository mentionRepo;

    @BeforeEach
    void setUp() {
        mentionRepo.deleteAll();
        muteRepo.deleteAll();
        blockRepo.deleteAll();
        followRepo.deleteAll();
        likeRepo.deleteAll();
        commentRepo.deleteAll();
        postRepo.deleteAll();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void threaded_reply_links_to_parent() {
        authenticateUser(1L, "alice");
        PostResponse post = communityService.createPost(
                new CreatePostRequest(ORG, "Test", "Hello world", null), "127.0.0.1");

        CommentResponse root = communityService.createComment(
                new CreateCommentRequest(post.id(), "Root comment", null, null), "127.0.0.1");

        CommentResponse reply = communityService.createComment(
                new CreateCommentRequest(post.id(), "Reply to root", root.id(), null), "127.0.0.1");

        assertEquals(root.id(), reply.parentCommentId());
    }

    @Test
    void reply_to_different_post_comment_is_rejected() {
        authenticateUser(1L, "alice");
        PostResponse post1 = communityService.createPost(
                new CreatePostRequest(ORG, "P1", "Body1", null), "127.0.0.1");
        PostResponse post2 = communityService.createPost(
                new CreatePostRequest(ORG, "P2", "Body2", null), "127.0.0.1");

        CommentResponse c1 = communityService.createComment(
                new CreateCommentRequest(post1.id(), "On P1", null, null), "127.0.0.1");

        var ex = assertThrows(BusinessException.class, () ->
                communityService.createComment(
                        new CreateCommentRequest(post2.id(), "Cross-thread", c1.id(), null),
                        "127.0.0.1"));
        assertEquals("INVALID_PARENT", ex.getCode());
    }

    @Test
    void like_is_idempotent_and_unlike_removes() {
        authenticateUser(1L, "alice");
        PostResponse post = communityService.createPost(
                new CreatePostRequest(ORG, "Likeable", "Content", null), "127.0.0.1");

        LikeStatusResponse first = communityService.likePost(post.id(), "127.0.0.1");
        assertTrue(first.likedByMe());
        assertEquals(1, first.likeCount());

        // Idempotent
        LikeStatusResponse second = communityService.likePost(post.id(), "127.0.0.1");
        assertEquals(1, second.likeCount());

        // Unlike
        LikeStatusResponse unliked = communityService.unlikePost(post.id(), "127.0.0.1");
        assertFalse(unliked.likedByMe());
        assertEquals(0, unliked.likeCount());
    }

    @Test
    void follow_self_is_rejected() {
        authenticateUser(1L, "alice");
        var ex = assertThrows(BusinessException.class,
                () -> communityService.follow(1L, "127.0.0.1"));
        assertEquals("CANNOT_FOLLOW_SELF", ex.getCode());
    }

    @Test
    void blocked_user_posts_are_hidden_from_feed() {
        authenticateUser(1L, "alice");
        PostResponse alicePost = communityService.createPost(
                new CreatePostRequest(ORG, "Alice Post", "by alice", null), "127.0.0.1");

        authenticateUser(2L, "bob");
        PostResponse bobPost = communityService.createPost(
                new CreatePostRequest(ORG, "Bob Post", "by bob", null), "127.0.0.1");

        // Bob blocks Alice
        communityService.blockUser(1L, "127.0.0.1");

        // Bob's feed should not show Alice's post
        List<PostResponse> bobFeed = communityService.listPosts();
        assertTrue(bobFeed.stream().noneMatch(p -> p.id().equals(alicePost.id())));
        assertTrue(bobFeed.stream().anyMatch(p -> p.id().equals(bobPost.id())));

        // Unblock restores feed
        communityService.unblockUser(1L, "127.0.0.1");
        List<PostResponse> bobFeedAfter = communityService.listPosts();
        assertTrue(bobFeedAfter.stream().anyMatch(p -> p.id().equals(alicePost.id())));
    }

    @Test
    void moderation_restore_hidden_post_returns_to_published() {
        authenticateUser(1L, "alice");
        PostResponse post = communityService.createPost(
                new CreatePostRequest(ORG, "To Hide", "Content", null), "127.0.0.1");

        // Hide through the public moderation flow so the test follows the same
        // path the application uses in production.
        var report = moderationService.file(
                new CreateModerationReportRequest(
                        ModerationReport.TargetType.POST, post.id(),
                        "ABUSE", "Needs hide before restore"),
                "127.0.0.1");
        moderationService.resolve(
                report.id(),
                new ResolveReportRequest(
                        ModerationReport.Status.UPHELD,
                        "Hidden for restore test",
                        "Hidden for restore test",
                        true),
                "127.0.0.1");

        // Verify hidden
        List<PostResponse> feed = communityService.listPosts();
        assertTrue(feed.stream().noneMatch(p -> p.id().equals(post.id())));

        // Restore
        moderationService.restore(ModerationReport.TargetType.POST, post.id(), "127.0.0.1");

        // Verify restored and visible
        List<PostResponse> feedAfter = communityService.listPosts();
        PostResponse restored = feedAfter.stream()
                .filter(p -> p.id().equals(post.id()))
                .findFirst().orElse(null);
        assertNotNull(restored);
        assertNotNull(restored.restoredAt());
    }

    @Test
    void restore_non_hidden_post_is_rejected() {
        authenticateUser(1L, "alice");
        PostResponse post = communityService.createPost(
                new CreatePostRequest(ORG, "Published", "Content", null), "127.0.0.1");

        var ex = assertThrows(BusinessException.class, () ->
                moderationService.restore(ModerationReport.TargetType.POST, post.id(), "127.0.0.1"));
        assertEquals("NOT_HIDDEN", ex.getCode());
    }

    private static void authenticateUser(Long userId, String username) {
        CurrentUser user = new CurrentUser(userId, username, username,
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("community.read", "community.write", "moderation.review"));
        var auth = new UsernamePasswordAuthenticationToken(
                user, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("community.read"),
                        new SimpleGrantedAuthority("community.write"),
                        new SimpleGrantedAuthority("moderation.review")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
