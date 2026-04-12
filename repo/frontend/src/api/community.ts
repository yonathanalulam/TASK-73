import { http } from './client';

export interface CommunityPost {
  id: number;
  organizationId: number;
  authorUserId: number;
  title: string | null;
  body: string;
  visibility: 'ORGANIZATION' | 'PUBLIC';
  status: string;
  hiddenReason: string | null;
  createdAt: string;
  updatedAt: string | null;
  hiddenAt: string | null;
  restoredAt: string | null;
}

export interface Comment {
  id: number;
  postId: number;
  organizationId: number;
  authorUserId: number;
  body: string;
  parentCommentId: number | null;
  quotedCommentId: number | null;
  status: string;
  hiddenReason: string | null;
  createdAt: string;
  hiddenAt: string | null;
  restoredAt: string | null;
}

export interface LikeStatus {
  postId: number | null;
  commentId: number | null;
  likeCount: number;
  likedByMe: boolean;
}

export const communityApi = {
  async listPosts(): Promise<CommunityPost[]> {
    const res = await http.get<CommunityPost[]>('/api/community/posts');
    return res.data;
  },
  async createPost(payload: { organizationId: number; title?: string; body: string; visibility?: string }): Promise<CommunityPost> {
    const res = await http.post<CommunityPost>('/api/community/posts', payload);
    return res.data;
  },
  async listComments(postId: number): Promise<Comment[]> {
    const res = await http.get<Comment[]>(`/api/community/posts/${postId}/comments`);
    return res.data;
  },
  async createComment(payload: { postId: number; body: string; parentCommentId?: number; quotedCommentId?: number }): Promise<Comment> {
    const res = await http.post<Comment>('/api/community/comments', payload);
    return res.data;
  },
  async likePost(postId: number): Promise<LikeStatus> {
    const res = await http.post<LikeStatus>(`/api/community/posts/${postId}/like`);
    return res.data;
  },
  async unlikePost(postId: number): Promise<LikeStatus> {
    const res = await http.delete<LikeStatus>(`/api/community/posts/${postId}/like`);
    return res.data;
  },
  async follow(userId: number): Promise<void> {
    await http.post(`/api/community/users/${userId}/follow`);
  },
  async unfollow(userId: number): Promise<void> {
    await http.delete(`/api/community/users/${userId}/follow`);
  },
  async muteThread(postId: number): Promise<void> {
    await http.post(`/api/community/posts/${postId}/mute`);
  },
  async unmuteThread(postId: number): Promise<void> {
    await http.delete(`/api/community/posts/${postId}/mute`);
  },
  async blockUser(userId: number): Promise<void> {
    await http.post(`/api/community/users/${userId}/block`);
  },
  async unblockUser(userId: number): Promise<void> {
    await http.delete(`/api/community/users/${userId}/block`);
  },
};
