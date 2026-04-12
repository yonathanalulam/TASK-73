package com.dojostay.community;

import com.dojostay.common.ApiResponse;
import com.dojostay.community.dto.CommentResponse;
import com.dojostay.community.dto.CreateCommentRequest;
import com.dojostay.community.dto.CreatePostRequest;
import com.dojostay.community.dto.FollowResponse;
import com.dojostay.community.dto.LikeStatusResponse;
import com.dojostay.community.dto.PostResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/posts")
    @PreAuthorize("hasAuthority('community.read')")
    public ApiResponse<List<PostResponse>> listPosts() {
        return ApiResponse.ok(communityService.listPosts());
    }

    @PostMapping("/posts")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<PostResponse> createPost(@Valid @RequestBody CreatePostRequest req,
                                                HttpServletRequest httpReq) {
        return ApiResponse.ok(communityService.createPost(req, clientIp(httpReq)));
    }

    @GetMapping("/posts/{postId}/comments")
    @PreAuthorize("hasAuthority('community.read')")
    public ApiResponse<List<CommentResponse>> listComments(@PathVariable Long postId) {
        return ApiResponse.ok(communityService.listComments(postId));
    }

    @PostMapping("/comments")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<CommentResponse> createComment(@Valid @RequestBody CreateCommentRequest req,
                                                      HttpServletRequest httpReq) {
        return ApiResponse.ok(communityService.createComment(req, clientIp(httpReq)));
    }

    // ---- C8: Likes -------------------------------------------------------

    @PostMapping("/posts/{postId}/like")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<LikeStatusResponse> likePost(@PathVariable Long postId,
                                                    HttpServletRequest httpReq) {
        return ApiResponse.ok(communityService.likePost(postId, clientIp(httpReq)));
    }

    @DeleteMapping("/posts/{postId}/like")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<LikeStatusResponse> unlikePost(@PathVariable Long postId,
                                                      HttpServletRequest httpReq) {
        return ApiResponse.ok(communityService.unlikePost(postId, clientIp(httpReq)));
    }

    @PostMapping("/comments/{commentId}/like")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<LikeStatusResponse> likeComment(@PathVariable Long commentId,
                                                       HttpServletRequest httpReq) {
        return ApiResponse.ok(communityService.likeComment(commentId, clientIp(httpReq)));
    }

    @DeleteMapping("/comments/{commentId}/like")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<LikeStatusResponse> unlikeComment(@PathVariable Long commentId,
                                                         HttpServletRequest httpReq) {
        return ApiResponse.ok(communityService.unlikeComment(commentId, clientIp(httpReq)));
    }

    // ---- C8: Follows -----------------------------------------------------

    @PostMapping("/users/{userId}/follow")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<FollowResponse> follow(@PathVariable Long userId,
                                              HttpServletRequest httpReq) {
        return ApiResponse.ok(communityService.follow(userId, clientIp(httpReq)));
    }

    @DeleteMapping("/users/{userId}/follow")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<Void> unfollow(@PathVariable Long userId,
                                      HttpServletRequest httpReq) {
        communityService.unfollow(userId, clientIp(httpReq));
        return ApiResponse.empty();
    }

    @GetMapping("/me/following")
    @PreAuthorize("hasAuthority('community.read')")
    public ApiResponse<List<FollowResponse>> listFollowing() {
        return ApiResponse.ok(communityService.listFollowing());
    }

    @GetMapping("/me/followers")
    @PreAuthorize("hasAuthority('community.read')")
    public ApiResponse<List<FollowResponse>> listFollowers() {
        return ApiResponse.ok(communityService.listFollowers());
    }

    // ---- C8: Mute thread -------------------------------------------------

    @PostMapping("/posts/{postId}/mute")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<Void> muteThread(@PathVariable Long postId,
                                        HttpServletRequest httpReq) {
        communityService.muteThread(postId, clientIp(httpReq));
        return ApiResponse.empty();
    }

    @DeleteMapping("/posts/{postId}/mute")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<Void> unmuteThread(@PathVariable Long postId,
                                          HttpServletRequest httpReq) {
        communityService.unmuteThread(postId, clientIp(httpReq));
        return ApiResponse.empty();
    }

    // ---- C8: Block user --------------------------------------------------

    @PostMapping("/users/{userId}/block")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<Void> blockUser(@PathVariable Long userId,
                                       HttpServletRequest httpReq) {
        communityService.blockUser(userId, clientIp(httpReq));
        return ApiResponse.empty();
    }

    @DeleteMapping("/users/{userId}/block")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<Void> unblockUser(@PathVariable Long userId,
                                         HttpServletRequest httpReq) {
        communityService.unblockUser(userId, clientIp(httpReq));
        return ApiResponse.empty();
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }
}
