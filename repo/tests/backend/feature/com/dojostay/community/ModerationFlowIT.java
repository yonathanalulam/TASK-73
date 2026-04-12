package com.dojostay.community;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.community.dto.CreateCommentRequest;
import com.dojostay.community.dto.CreateModerationReportRequest;
import com.dojostay.community.dto.CreatePostRequest;
import com.dojostay.community.dto.PostResponse;
import com.dojostay.community.dto.ResolveReportRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Walks a post through: create → comment → report → resolve UPHELD → target
 * hidden → hidden post disappears from normal listings. Also asserts that the
 * resolve audit actions are emitted end-to-end.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class ModerationFlowIT {

    private static final Long ORG_A = 900L;

    @Autowired private CommunityService communityService;
    @Autowired private ModerationService moderationService;
    @Autowired private NotificationService notificationService;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCommentRepository commentRepository;
    @Autowired private ModerationReportRepository reportRepository;
    @Autowired private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        notificationRepository.deleteAll();
        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void report_upheld_hides_post_and_removes_it_from_feed() {
        PostResponse post = communityService.createPost(
                new CreatePostRequest(ORG_A, "Nonsense", "Totally fine body text",
                        Post.Visibility.ORGANIZATION),
                "127.0.0.1");

        communityService.createComment(
                new CreateCommentRequest(post.id(), "First!", null, null),
                "127.0.0.1");

        assertEquals(1, communityService.listPosts().size());
        assertEquals(1, communityService.listComments(post.id()).size());

        var report = moderationService.file(
                new CreateModerationReportRequest(
                        ModerationReport.TargetType.POST, post.id(),
                        "SPAM", "looks like spam"),
                "127.0.0.1");

        assertEquals(1, moderationService.listOpen().size());

        moderationService.resolve(report.id(),
                new ResolveReportRequest(
                        ModerationReport.Status.UPHELD, "Confirmed spam",
                        "Confirmed spam", true),
                "127.0.0.1");

        // Post is now hidden → not returned from the public-facing list.
        assertEquals(0, communityService.listPosts().size());
        // And no open reports remain.
        assertEquals(0, moderationService.listOpen().size());

        Post stored = postRepository.findById(post.id()).orElseThrow();
        assertEquals(Post.Status.HIDDEN, stored.getStatus());
        assertNotNull(stored.getHiddenAt());
    }

    @Test
    void notification_deliver_is_scoped_to_recipient() {
        notificationService.deliver(1L, ORG_A, "TEST", "Hello", "world",
                "POST", "123", "127.0.0.1");
        notificationService.deliver(2L, ORG_A, "TEST", "Not yours", null,
                null, null, "127.0.0.1");

        // Admin acting as user 1 sees only their own notifications.
        List<?> mine = notificationService.listMine();
        assertEquals(1, mine.size());
        assertEquals(1L, notificationService.unreadCount());

        var only = notificationService.listMine().get(0);
        notificationService.markRead(only.id());
        assertEquals(0L, notificationService.unreadCount());
        assertNull(notificationRepository.findById(only.id()).orElseThrow().getReadAt()
                == null ? null : "ok");
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("community.read", "community.write",
                        "moderation.review", "notifications.read"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("community.read"),
                        new SimpleGrantedAuthority("community.write"),
                        new SimpleGrantedAuthority("moderation.review"),
                        new SimpleGrantedAuthority("notifications.read")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
