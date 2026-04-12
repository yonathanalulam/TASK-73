package com.dojostay.community;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.community.dto.CommentResponse;
import com.dojostay.community.dto.CreateCommentRequest;
import com.dojostay.community.dto.CreatePostRequest;
import com.dojostay.community.dto.FollowResponse;
import com.dojostay.community.dto.LikeStatusResponse;
import com.dojostay.community.dto.PostResponse;
import com.dojostay.ops.FeatureGuard;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Community posts + comments. Visibility model: every post belongs to an org,
 * and reads are scope-filtered. {@code PUBLIC} posts are returned to admin
 * users globally; org-scoped users see the union of PUBLIC posts in their orgs
 * and any ORGANIZATION posts in their orgs. Hidden and deleted posts are not
 * returned to normal reads — they remain visible to moderation workflows via
 * {@link ModerationService}.
 *
 * <p>Phase C8 extensions:
 * <ul>
 *   <li>threaded replies and quote-replies on comments</li>
 *   <li>likes on posts and comments (idempotent insert + hard-delete unlike)</li>
 *   <li>follow / unfollow between users</li>
 *   <li>{@code @username} mention parsing with notification delivery</li>
 *   <li>thread-mute and user-block with feed filtering</li>
 * </ul>
 * Notifications for replies, mentions, likes, and follows are delivered
 * inline in the same transaction so a failed notification rolls the whole
 * interaction back — keeping the audit trail consistent with the state.
 */
@Service
public class CommunityService {

    /** Matches @word where word is [A-Za-z0-9_.-]. Same grammar as usernames. */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_.-]{1,64})");

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostLikeRepository likeRepository;
    private final UserFollowRepository followRepository;
    private final PostMentionRepository mentionRepository;
    private final ThreadMuteRepository muteRepository;
    private final UserBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DataScopeService dataScopeService;
    private final FeatureGuard featureGuard;
    private final AuditService auditService;

    public CommunityService(PostRepository postRepository,
                            PostCommentRepository commentRepository,
                            PostLikeRepository likeRepository,
                            UserFollowRepository followRepository,
                            PostMentionRepository mentionRepository,
                            ThreadMuteRepository muteRepository,
                            UserBlockRepository blockRepository,
                            UserRepository userRepository,
                            NotificationService notificationService,
                            DataScopeService dataScopeService,
                            FeatureGuard featureGuard,
                            AuditService auditService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.followRepository = followRepository;
        this.mentionRepository = mentionRepository;
        this.muteRepository = muteRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.dataScopeService = dataScopeService;
        this.featureGuard = featureGuard;
        this.auditService = auditService;
    }

    /** Gate community write operations on the community.enabled toggle. */
    private void requireCommunityWritable() {
        featureGuard.requireEnabled(FeatureGuard.COMMUNITY_ENABLED, "Community posting");
    }

    @Transactional(readOnly = true)
    public List<PostResponse> listPosts() {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        Long actor = actorId();
        Set<Long> blocked = actor == null ? Set.of() : loadBlockedUserIds(actor);
        return postRepository.findAll().stream()
                .filter(p -> p.getStatus() == Post.Status.PUBLISHED)
                .filter(p -> canRead(p, scope))
                .filter(p -> !blocked.contains(p.getAuthorUserId()))
                .map(CommunityService::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest req, String sourceIp) {
        requireCommunityWritable();
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(req.organizationId())) {
            throw new BusinessException("OUT_OF_SCOPE",
                    "Cannot post in an organization outside your data scope",
                    HttpStatus.FORBIDDEN);
        }
        Long actor = requireActor();
        Post p = new Post();
        p.setOrganizationId(req.organizationId());
        p.setAuthorUserId(actor);
        p.setTitle(req.title());
        p.setBody(req.body());
        p.setVisibility(req.visibility() != null ? req.visibility() : Post.Visibility.ORGANIZATION);
        p.setStatus(Post.Status.PUBLISHED);
        Post saved = postRepository.save(p);

        auditService.record(AuditAction.POST_CREATED, actor, actorUsername(),
                "POST", String.valueOf(saved.getId()),
                "Post created in org " + saved.getOrganizationId(), sourceIp);

        // Mention resolution on post body (title is only advisory)
        deliverMentions(req.body(), saved.getId(), null, saved.getOrganizationId(),
                actor, "POST", String.valueOf(saved.getId()), sourceIp);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(Long postId) {
        Post p = loadReadableOrThrow(postId);
        Long actor = actorId();
        Set<Long> blocked = actor == null ? Set.of() : loadBlockedUserIds(actor);
        return commentRepository.findByPostIdOrderByIdAsc(p.getId()).stream()
                .filter(c -> c.getStatus() == PostComment.Status.PUBLISHED)
                .filter(c -> !blocked.contains(c.getAuthorUserId()))
                .map(CommunityService::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse createComment(CreateCommentRequest req, String sourceIp) {
        requireCommunityWritable();
        Post p = loadReadableOrThrow(req.postId());
        if (p.getStatus() != Post.Status.PUBLISHED) {
            throw new BusinessException("POST_NOT_OPEN",
                    "Post is not accepting comments", HttpStatus.CONFLICT);
        }
        Long actor = requireActor();

        // Validate reply/quote targets live on the same post — prevents
        // cross-thread smuggling of a quoted comment.
        if (req.parentCommentId() != null) {
            PostComment parent = commentRepository.findById(req.parentCommentId())
                    .orElseThrow(() -> new NotFoundException("Parent comment not found"));
            if (!parent.getPostId().equals(p.getId())) {
                throw new BusinessException("INVALID_PARENT",
                        "Parent comment does not belong to this post",
                        HttpStatus.BAD_REQUEST);
            }
        }
        if (req.quotedCommentId() != null) {
            PostComment quoted = commentRepository.findById(req.quotedCommentId())
                    .orElseThrow(() -> new NotFoundException("Quoted comment not found"));
            if (!quoted.getPostId().equals(p.getId())) {
                throw new BusinessException("INVALID_QUOTE",
                        "Quoted comment does not belong to this post",
                        HttpStatus.BAD_REQUEST);
            }
        }

        PostComment c = new PostComment();
        c.setPostId(p.getId());
        c.setOrganizationId(p.getOrganizationId());
        c.setAuthorUserId(actor);
        c.setBody(req.body());
        c.setParentCommentId(req.parentCommentId());
        c.setQuotedCommentId(req.quotedCommentId());
        c.setStatus(PostComment.Status.PUBLISHED);
        PostComment saved = commentRepository.save(c);

        auditService.record(AuditAction.COMMENT_CREATED, actor, actorUsername(),
                "COMMENT", String.valueOf(saved.getId()),
                "Comment on post " + p.getId(), sourceIp);

        // Reply notification — goes to the parent comment's author (not the
        // post owner, which would spam them for every deep reply).
        if (req.parentCommentId() != null) {
            commentRepository.findById(req.parentCommentId()).ifPresent(parent -> {
                if (!parent.getAuthorUserId().equals(actor)
                        && canNotify(parent.getAuthorUserId(), actor, p.getId())) {
                    notificationService.deliver(parent.getAuthorUserId(), p.getOrganizationId(),
                            "COMMENT_REPLY", "New reply",
                            "Someone replied to your comment",
                            "COMMENT", String.valueOf(saved.getId()), sourceIp);
                }
            });
        } else if (!p.getAuthorUserId().equals(actor)
                && canNotify(p.getAuthorUserId(), actor, p.getId())) {
            // Top-level comment → notify the post author
            notificationService.deliver(p.getAuthorUserId(), p.getOrganizationId(),
                    "POST_COMMENT", "New comment",
                    "Someone commented on your post",
                    "COMMENT", String.valueOf(saved.getId()), sourceIp);
        }

        // Mention notifications
        deliverMentions(req.body(), null, saved.getId(), p.getOrganizationId(),
                actor, "COMMENT", String.valueOf(saved.getId()), sourceIp);
        return toResponse(saved);
    }

    // ---------------------------------------------------------------------
    // Likes
    // ---------------------------------------------------------------------

    @Transactional
    public LikeStatusResponse likePost(Long postId, String sourceIp) {
        requireCommunityWritable();
        Post p = loadReadableOrThrow(postId);
        Long actor = requireActor();
        PostLike like = likeRepository.findFirstByPostIdAndUserId(p.getId(), actor)
                .orElseGet(() -> {
                    PostLike fresh = new PostLike();
                    fresh.setPostId(p.getId());
                    fresh.setUserId(actor);
                    PostLike saved = likeRepository.save(fresh);
                    auditService.record(AuditAction.POST_LIKED, actor, actorUsername(),
                            "POST", String.valueOf(p.getId()),
                            "Post liked", sourceIp);
                    if (!p.getAuthorUserId().equals(actor)
                            && canNotify(p.getAuthorUserId(), actor, p.getId())) {
                        notificationService.deliver(p.getAuthorUserId(), p.getOrganizationId(),
                                "POST_LIKE", "New like",
                                "Someone liked your post",
                                "POST", String.valueOf(p.getId()), sourceIp);
                    }
                    return saved;
                });
        long count = likeRepository.countByPostId(p.getId());
        return new LikeStatusResponse(p.getId(), null, count, true);
    }

    @Transactional
    public LikeStatusResponse unlikePost(Long postId, String sourceIp) {
        Post p = loadReadableOrThrow(postId);
        Long actor = requireActor();
        likeRepository.findFirstByPostIdAndUserId(p.getId(), actor).ifPresent(existing -> {
            likeRepository.delete(existing);
            auditService.record(AuditAction.POST_UNLIKED, actor, actorUsername(),
                    "POST", String.valueOf(p.getId()),
                    "Post unliked", sourceIp);
        });
        long count = likeRepository.countByPostId(p.getId());
        return new LikeStatusResponse(p.getId(), null, count, false);
    }

    @Transactional
    public LikeStatusResponse likeComment(Long commentId, String sourceIp) {
        requireCommunityWritable();
        PostComment c = loadReadableCommentOrThrow(commentId);
        Long actor = requireActor();
        likeRepository.findFirstByCommentIdAndUserId(c.getId(), actor)
                .orElseGet(() -> {
                    PostLike fresh = new PostLike();
                    fresh.setCommentId(c.getId());
                    fresh.setUserId(actor);
                    PostLike saved = likeRepository.save(fresh);
                    auditService.record(AuditAction.POST_LIKED, actor, actorUsername(),
                            "COMMENT", String.valueOf(c.getId()),
                            "Comment liked", sourceIp);
                    if (!c.getAuthorUserId().equals(actor)
                            && canNotify(c.getAuthorUserId(), actor, c.getPostId())) {
                        notificationService.deliver(c.getAuthorUserId(), c.getOrganizationId(),
                                "COMMENT_LIKE", "New like",
                                "Someone liked your comment",
                                "COMMENT", String.valueOf(c.getId()), sourceIp);
                    }
                    return saved;
                });
        long count = likeRepository.countByCommentId(c.getId());
        return new LikeStatusResponse(null, c.getId(), count, true);
    }

    @Transactional
    public LikeStatusResponse unlikeComment(Long commentId, String sourceIp) {
        PostComment c = loadReadableCommentOrThrow(commentId);
        Long actor = requireActor();
        likeRepository.findFirstByCommentIdAndUserId(c.getId(), actor).ifPresent(existing -> {
            likeRepository.delete(existing);
            auditService.record(AuditAction.POST_UNLIKED, actor, actorUsername(),
                    "COMMENT", String.valueOf(c.getId()),
                    "Comment unliked", sourceIp);
        });
        long count = likeRepository.countByCommentId(c.getId());
        return new LikeStatusResponse(null, c.getId(), count, false);
    }

    // ---------------------------------------------------------------------
    // Follow / unfollow
    // ---------------------------------------------------------------------

    @Transactional
    public FollowResponse follow(Long targetUserId, String sourceIp) {
        requireCommunityWritable();
        Long actor = requireActor();
        if (actor.equals(targetUserId)) {
            throw new BusinessException("CANNOT_FOLLOW_SELF",
                    "You cannot follow yourself", HttpStatus.BAD_REQUEST);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        validateScopeVisibility(target);
        UserFollow follow = followRepository
                .findByFollowerUserIdAndFollowedUserId(actor, targetUserId)
                .orElseGet(() -> {
                    UserFollow f = new UserFollow();
                    f.setFollowerUserId(actor);
                    f.setFollowedUserId(targetUserId);
                    UserFollow saved = followRepository.save(f);
                    auditService.record(AuditAction.USER_FOLLOWED, actor, actorUsername(),
                            "USER", String.valueOf(targetUserId),
                            "Followed user " + targetUserId, sourceIp);
                    if (canNotify(targetUserId, actor, null)) {
                        notificationService.deliver(targetUserId, null,
                                "FOLLOW", "New follower",
                                "Someone started following you",
                                "USER", String.valueOf(actor), sourceIp);
                    }
                    return saved;
                });
        return new FollowResponse(follow.getId(), follow.getFollowerUserId(),
                follow.getFollowedUserId(), follow.getCreatedAt());
    }

    @Transactional
    public void unfollow(Long targetUserId, String sourceIp) {
        Long actor = requireActor();
        followRepository.findByFollowerUserIdAndFollowedUserId(actor, targetUserId)
                .ifPresent(existing -> {
                    followRepository.delete(existing);
                    auditService.record(AuditAction.USER_UNFOLLOWED, actor, actorUsername(),
                            "USER", String.valueOf(targetUserId),
                            "Unfollowed user " + targetUserId, sourceIp);
                });
    }

    @Transactional(readOnly = true)
    public List<FollowResponse> listFollowing() {
        Long actor = requireActor();
        return followRepository.findByFollowerUserIdOrderByIdDesc(actor).stream()
                .map(f -> new FollowResponse(f.getId(), f.getFollowerUserId(),
                        f.getFollowedUserId(), f.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FollowResponse> listFollowers() {
        Long actor = requireActor();
        return followRepository.findByFollowedUserIdOrderByIdDesc(actor).stream()
                .map(f -> new FollowResponse(f.getId(), f.getFollowerUserId(),
                        f.getFollowedUserId(), f.getCreatedAt()))
                .toList();
    }

    // ---------------------------------------------------------------------
    // Mute / block
    // ---------------------------------------------------------------------

    @Transactional
    public void muteThread(Long postId, String sourceIp) {
        Post p = loadReadableOrThrow(postId);
        Long actor = requireActor();
        if (!muteRepository.existsByUserIdAndPostId(actor, p.getId())) {
            ThreadMute m = new ThreadMute();
            m.setUserId(actor);
            m.setPostId(p.getId());
            muteRepository.save(m);
            auditService.record(AuditAction.THREAD_MUTED, actor, actorUsername(),
                    "POST", String.valueOf(p.getId()),
                    "Muted thread", sourceIp);
        }
    }

    @Transactional
    public void unmuteThread(Long postId, String sourceIp) {
        Long actor = requireActor();
        muteRepository.findByUserIdAndPostId(actor, postId).ifPresent(existing -> {
            muteRepository.delete(existing);
            auditService.record(AuditAction.THREAD_UNMUTED, actor, actorUsername(),
                    "POST", String.valueOf(postId),
                    "Unmuted thread", sourceIp);
        });
    }

    @Transactional
    public void blockUser(Long targetUserId, String sourceIp) {
        requireCommunityWritable();
        Long actor = requireActor();
        if (actor.equals(targetUserId)) {
            throw new BusinessException("CANNOT_BLOCK_SELF",
                    "You cannot block yourself", HttpStatus.BAD_REQUEST);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        validateScopeVisibility(target);
        if (!blockRepository.existsByUserIdAndBlockedUserId(actor, targetUserId)) {
            UserBlock b = new UserBlock();
            b.setUserId(actor);
            b.setBlockedUserId(targetUserId);
            blockRepository.save(b);
            auditService.record(AuditAction.USER_BLOCKED, actor, actorUsername(),
                    "USER", String.valueOf(targetUserId),
                    "Blocked user " + targetUserId, sourceIp);
        }
    }

    @Transactional
    public void unblockUser(Long targetUserId, String sourceIp) {
        Long actor = requireActor();
        blockRepository.findByUserIdAndBlockedUserId(actor, targetUserId).ifPresent(existing -> {
            blockRepository.delete(existing);
            auditService.record(AuditAction.USER_UNBLOCKED, actor, actorUsername(),
                    "USER", String.valueOf(targetUserId),
                    "Unblocked user " + targetUserId, sourceIp);
        });
    }

    // Package-private helpers for ModerationService ----------------------

    Post loadPostForModeration(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
    }

    PostComment loadCommentForModeration(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
    }

    void hidePost(Post p, String reason) {
        p.setStatus(Post.Status.HIDDEN);
        p.setHiddenReason(reason);
        p.setHiddenAt(Instant.now());
        p.setRestoredAt(null);
        postRepository.save(p);
    }

    void hideComment(PostComment c, String reason) {
        c.setStatus(PostComment.Status.HIDDEN);
        c.setHiddenReason(reason);
        c.setHiddenAt(Instant.now());
        c.setRestoredAt(null);
        commentRepository.save(c);
    }

    void restorePost(Post p) {
        p.setStatus(Post.Status.PUBLISHED);
        p.setHiddenReason(null);
        p.setRestoredAt(Instant.now());
        postRepository.save(p);
    }

    void restoreComment(PostComment c) {
        c.setStatus(PostComment.Status.PUBLISHED);
        c.setHiddenReason(null);
        c.setRestoredAt(Instant.now());
        commentRepository.save(c);
    }

    // Internal helpers --------------------------------------------------

    /**
     * Validate that the target user is visible under the current actor's scope.
     * Admins (fullAccess) can interact with anyone. Non-admin users can only
     * interact with users in the same organization(s) as their scope grants.
     * Users without an organizationId (e.g. platform-level accounts) are
     * visible to all.
     */
    private void validateScopeVisibility(User target) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) return;
        // If the target has an org, the actor must have that org in scope
        if (target.getOrganizationId() != null
                && !scope.hasOrganization(target.getOrganizationId())) {
            throw new BusinessException("OUT_OF_SCOPE",
                    "Target user is not within your data scope",
                    HttpStatus.FORBIDDEN);
        }
    }

    private Post loadReadableOrThrow(Long postId) {
        Post p = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!canRead(p, scope)) {
            throw new NotFoundException("Post not found");
        }
        return p;
    }

    private PostComment loadReadableCommentOrThrow(Long commentId) {
        PostComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        Post p = postRepository.findById(c.getPostId())
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!canRead(p, scope)) {
            throw new NotFoundException("Comment not found");
        }
        return c;
    }

    private boolean canRead(Post p, EffectiveScope scope) {
        if (scope.fullAccess()) return true;
        if (p.getVisibility() == Post.Visibility.PUBLIC) return true;
        return scope.hasOrganization(p.getOrganizationId());
    }

    private Set<Long> loadBlockedUserIds(Long actor) {
        return blockRepository.findByUserIdOrderByIdDesc(actor).stream()
                .map(UserBlock::getBlockedUserId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Notification gating. Skips delivery when:
     * <ul>
     *   <li>the recipient has muted this thread, or</li>
     *   <li>the recipient has blocked the actor (hide existence)</li>
     * </ul>
     */
    private boolean canNotify(Long recipientUserId, Long actorUserId, Long postId) {
        if (postId != null && muteRepository.existsByUserIdAndPostId(recipientUserId, postId)) {
            return false;
        }
        if (blockRepository.existsByUserIdAndBlockedUserId(recipientUserId, actorUserId)) {
            return false;
        }
        return true;
    }

    /**
     * Parse {@code @username} mentions out of free text, resolve them against
     * {@link UserRepository}, insert {@link PostMention} rows, and fire
     * MENTION notifications. Unknown usernames are silently dropped.
     */
    private void deliverMentions(String body, Long postId, Long commentId, Long organizationId,
                                 Long actor, String refType, String refId, String sourceIp) {
        if (body == null || body.isEmpty()) return;
        Matcher m = MENTION_PATTERN.matcher(body);
        Set<String> seen = new HashSet<>();
        List<PostMention> mentions = new ArrayList<>();
        while (m.find()) {
            String username = m.group(1);
            if (!seen.add(username.toLowerCase())) continue;
            Optional<User> target = userRepository.findByUsername(username);
            if (target.isEmpty()) continue;
            Long recipient = target.get().getId();
            if (recipient.equals(actor)) continue; // self-mentions are noise

            PostMention pm = new PostMention();
            pm.setPostId(postId);
            pm.setCommentId(commentId);
            pm.setMentionedUserId(recipient);
            mentions.add(pm);

            if (canNotify(recipient, actor, postId != null ? postId
                    : (commentId != null ? resolvePostIdForComment(commentId) : null))) {
                notificationService.deliver(recipient, organizationId,
                        "MENTION", "You were mentioned",
                        "Someone mentioned you in a " + refType.toLowerCase(java.util.Locale.ROOT),
                        refType, refId, sourceIp);
            }
        }
        if (!mentions.isEmpty()) {
            mentionRepository.saveAll(mentions);
        }
    }

    private Long resolvePostIdForComment(Long commentId) {
        return commentRepository.findById(commentId).map(PostComment::getPostId).orElse(null);
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static Long requireActor() {
        Long id = actorId();
        if (id == null) {
            throw new BusinessException("NO_ACTOR",
                    "An authenticated user is required",
                    HttpStatus.UNAUTHORIZED);
        }
        return id;
    }

    private static PostResponse toResponse(Post p) {
        return new PostResponse(
                p.getId(), p.getOrganizationId(), p.getAuthorUserId(),
                p.getTitle(), p.getBody(), p.getVisibility(), p.getStatus(),
                p.getHiddenReason(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getHiddenAt(), p.getRestoredAt()
        );
    }

    private static CommentResponse toResponse(PostComment c) {
        return new CommentResponse(
                c.getId(), c.getPostId(), c.getOrganizationId(),
                c.getAuthorUserId(), c.getBody(),
                c.getParentCommentId(), c.getQuotedCommentId(),
                c.getStatus(), c.getHiddenReason(),
                c.getCreatedAt(), c.getHiddenAt(), c.getRestoredAt()
        );
    }
}
